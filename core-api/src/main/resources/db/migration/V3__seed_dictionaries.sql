-- Stage 1: seed dictionaries referenced by ingestion and search.
-- Migrations run once per database; `on conflict do nothing` keeps manual re-runs safe.

insert into professions (code, name)
values ('ANDROID', 'Android Developer'),
       ('JAVA_BACKEND', 'Java Backend Developer'),
       ('SYSTEM_ANALYST', 'System Analyst'),
       ('DATA_ANALYST', 'Data Analyst'),
       ('BI_ANALYST', 'BI Analyst'),
       ('QA', 'QA Engineer'),
       ('DEVOPS', 'DevOps Engineer'),
       ('FRONTEND', 'Frontend Developer'),
       ('ML', 'ML Engineer'),
       ('OTHER', 'Other')
on conflict (code) do nothing;

insert into skills (code, name, aliases, category)
values ('KOTLIN', 'Kotlin', array['Kotlin', 'Kotlin/JVM'], 'language'),
       ('JAVA', 'Java', array['Java', 'Java 21'], 'language'),
       ('JETPACK_COMPOSE', 'Jetpack Compose', array['Jetpack Compose', 'Compose'], 'android'),
       ('COROUTINES', 'Coroutines', array['Coroutines', 'Kotlin Coroutines'], 'android'),
       ('FLOW', 'Flow', array['Flow', 'Kotlin Flow', 'Coroutines Flow'], 'android'),
       ('MVVM', 'MVVM', array['MVVM'], 'architecture'),
       ('CLEAN_ARCHITECTURE', 'Clean Architecture', array['Clean Architecture', 'Clean Arch'], 'architecture'),
       ('HILT', 'Hilt', array['Hilt', 'Dagger Hilt'], 'android'),
       ('ROOM', 'Room', array['Room', 'Room DB'], 'android'),
       ('RETROFIT', 'Retrofit', array['Retrofit'], 'android'),
       ('OKHTTP', 'OkHttp', array['OkHttp', 'Ok HTTP'], 'android'),
       ('WORKMANAGER', 'WorkManager', array['WorkManager'], 'android'),
       ('SPRING_BOOT', 'Spring Boot', array['Spring Boot', 'SpringBoot', 'Spring'], 'backend'),
       ('POSTGRESQL', 'PostgreSQL', array['PostgreSQL', 'Postgres'], 'database'),
       ('SQL', 'SQL', array['SQL'], 'database'),
       ('REST', 'REST', array['REST', 'REST API', 'RESTful'], 'backend'),
       ('DOCKER', 'Docker', array['Docker'], 'devops'),
       ('GIT', 'Git', array['Git', 'GitHub', 'GitLab'], 'tool'),
       ('JUNIT', 'JUnit', array['JUnit', 'JUnit 5', 'JUnit5'], 'testing'),
       ('PYTHON', 'Python', array['Python', 'Python 3'], 'language'),
       ('PANDAS', 'pandas', array['pandas', 'Pandas'], 'data'),
       ('POWER_BI', 'Power BI', array['Power BI', 'PowerBI'], 'bi'),
       ('BPMN', 'BPMN', array['BPMN', 'BPMN 2.0'], 'process'),
       ('UML', 'UML', array['UML'], 'process'),
       ('OPENAPI', 'OpenAPI', array['OpenAPI', 'Swagger'], 'backend')
on conflict (code) do nothing;

insert into sources (code, name, base_url)
values ('HH', 'HeadHunter', 'https://hh.ru'),
       ('YANDEX', 'Yandex', 'https://yandex.ru/jobs'),
       ('SBER', 'Sber', 'https://rabota.sber.ru'),
       ('VK', 'VK', 'https://team.vk.company'),
       ('TBANK', 'T-Bank', 'https://www.tbank.ru/career/'),
       ('OZON', 'Ozon', 'https://job.ozon.ru'),
       ('OTHER', 'Other', null)
on conflict (code) do nothing;
