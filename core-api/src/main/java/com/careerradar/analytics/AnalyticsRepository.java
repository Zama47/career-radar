package com.careerradar.analytics;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Native aggregate queries for market analytics. Kept separate from {@code VacancyRepository}
 * because every query shares a dynamic filter and is easier to read as SQL.
 *
 * <p>"Market date" is {@code coalesce(published_at, first_seen_at)}: sources are not required
 * to publish a date, but the vacancy always has the date we first saw it. Salary is
 * {@code coalesce(salary_from, salary_to)} so a range with only an upper bound still counts.
 */
@Repository
public class AnalyticsRepository {

    private static final String MARKET_DATE = "coalesce(v.published_at, v.first_seen_at)";
    private static final String SALARY = "coalesce(v.salary_from, v.salary_to)";

    private final JdbcClient jdbcClient;

    public AnalyticsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public OverviewRow overview(AnalyticsFilter filter) {
        SqlFilter where = marketFilter(filter);
        SqlFilter newWindow = newWindowFilter(filter);
        where.params().putAll(newWindow.params());

        String sql = """
                select count(*) as total,
                       count(*) filter (where %s) as new_count,
                       percentile_cont(0.5) within group (order by %s) as p50,
                       percentile_cont(0.25) within group (order by %s) as p25,
                       percentile_cont(0.75) within group (order by %s) as p75,
                       count(*) filter (where v.remote_type = 'REMOTE') as remote_count,
                       mode() within group (order by v.currency) as currency
                  from vacancies v
                  left join professions p on p.id = v.profession_id
                 where %s
                """.formatted(newWindow.sql(), SALARY, SALARY, SALARY, where.sql());

        return jdbcClient.sql(sql)
                .params(where.params())
                .query((rs, rowNum) -> new OverviewRow(
                        rs.getLong("total"),
                        rs.getLong("new_count"),
                        rs.getObject("p50", Double.class),
                        rs.getObject("p25", Double.class),
                        rs.getObject("p75", Double.class),
                        rs.getLong("remote_count"),
                        rs.getString("currency")))
                .single();
    }

    public long count(AnalyticsFilter filter) {
        SqlFilter where = marketFilter(filter);
        String sql = """
                select count(*)
                  from vacancies v
                  left join professions p on p.id = v.profession_id
                 where %s
                """.formatted(where.sql());
        return jdbcClient.sql(sql)
                .params(where.params())
                .query(Long.class)
                .single();
    }

    public List<AnalyticsOverviewResponse.TopProfession> topProfessions(AnalyticsFilter filter, int limit) {
        SqlFilter where = marketFilter(filter);
        String sql = """
                select p.code          as code,
                       p.name          as name,
                       count(*)        as vacancy_count
                  from vacancies v
                  join professions p on p.id = v.profession_id
                 where %s
                 group by p.code, p.name
                 order by count(*) desc, p.code
                 limit :topLimit
                """.formatted(where.sql());
        where.params().put("topLimit", limit);

        return jdbcClient.sql(sql)
                .params(where.params())
                .query((rs, rowNum) -> new AnalyticsOverviewResponse.TopProfession(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getLong("vacancy_count")))
                .list();
    }

    public List<AnalyticsOverviewResponse.TopSkill> topSkills(AnalyticsFilter filter, int limit) {
        SqlFilter where = marketFilter(filter);
        String sql = """
                select s.name          as skill_name,
                       count(*)        as vacancy_count
                  from vacancy_skills vs
                  join skills s on s.id = vs.skill_id
                  join vacancies v on v.id = vs.vacancy_id
                  left join professions p on p.id = v.profession_id
                 where %s
                 group by s.name
                 order by count(*) desc, s.name
                 limit :topLimit
                """.formatted(where.sql());
        where.params().put("topLimit", limit);

        return jdbcClient.sql(sql)
                .params(where.params())
                .query((rs, rowNum) -> new AnalyticsOverviewResponse.TopSkill(
                        rs.getString("skill_name"),
                        rs.getLong("vacancy_count")))
                .list();
    }

    public List<TimelineResponse.Point> timeline(AnalyticsFilter filter, TimelineBucket bucket) {
        SqlFilter where = marketFilter(filter);
        String sql = """
                select to_char(date_trunc(:bucket, %s at time zone 'UTC'), 'YYYY-MM-DD') as bucket_date,
                       count(*) as vacancy_count
                  from vacancies v
                  left join professions p on p.id = v.profession_id
                 where %s
                 group by 1
                 order by 1
                """.formatted(MARKET_DATE, where.sql());
        where.params().put("bucket", bucket.sqlValue());

        return jdbcClient.sql(sql)
                .params(where.params())
                .query((rs, rowNum) -> new TimelineResponse.Point(
                        rs.getString("bucket_date"),
                        rs.getLong("vacancy_count")))
                .list();
    }

    public SalaryRow salaries(AnalyticsFilter filter) {
        SqlFilter where = marketFilter(filter);
        String sql = """
                select percentile_cont(0.25) within group (order by %s) as p25,
                       percentile_cont(0.5) within group (order by %s) as p50,
                       percentile_cont(0.75) within group (order by %s) as p75,
                       count(%s) as sample_size,
                       mode() within group (order by v.currency) as currency
                  from vacancies v
                  left join professions p on p.id = v.profession_id
                 where %s
                """.formatted(SALARY, SALARY, SALARY, SALARY, where.sql());

        return jdbcClient.sql(sql)
                .params(where.params())
                .query((rs, rowNum) -> new SalaryRow(
                        rs.getObject("p25", Double.class),
                        rs.getObject("p50", Double.class),
                        rs.getObject("p75", Double.class),
                        rs.getLong("sample_size"),
                        rs.getString("currency")))
                .single();
    }

    private static SqlFilter marketFilter(AnalyticsFilter filter) {
        StringBuilder sql = new StringBuilder("v.status = 'ACTIVE'");
        Map<String, Object> params = new LinkedHashMap<>();

        if (filter.from() != null) {
            sql.append(" and ").append(MARKET_DATE).append(" >= :from");
            params.put("from", startOfDay(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" and ").append(MARKET_DATE).append(" < :toExclusive");
            params.put("toExclusive", startOfDay(filter.to().plusDays(1)));
        }
        if (filter.profession() != null) {
            sql.append(" and p.code = :profession");
            params.put("profession", filter.profession());
        }
        if (filter.seniority() != null) {
            sql.append(" and v.seniority = :seniority");
            params.put("seniority", filter.seniority().name());
        }
        if (filter.remote() != null) {
            sql.append(" and v.remote_type = :remote");
            params.put("remote", filter.remote().name());
        }
        return new SqlFilter(sql.toString(), params);
    }

    private static SqlFilter newWindowFilter(AnalyticsFilter filter) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();

        if (filter.from() != null) {
            sql.append("v.first_seen_at >= :newFrom");
            params.put("newFrom", startOfDay(filter.from()));
        } else {
            sql.append("v.first_seen_at >= :newFrom");
            params.put("newFrom", startOfDay(LocalDate.now(ZoneOffset.UTC)));
        }
        if (filter.to() != null) {
            sql.append(" and v.first_seen_at < :newToExclusive");
            params.put("newToExclusive", startOfDay(filter.to().plusDays(1)));
        }
        return new SqlFilter(sql.toString(), params);
    }

    private static java.time.OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private record SqlFilter(String sql, Map<String, Object> params) {
    }

    public record OverviewRow(
            long total,
            long newCount,
            Double p50,
            Double p25,
            Double p75,
            long remoteCount,
            String currency) {
    }

    public record SalaryRow(
            Double p25,
            Double p50,
            Double p75,
            long sampleSize,
            String currency) {
    }
}
