-- Stage 2: company career-site catalog.
--
-- The collector only sends VacancyDraft items for sources that exist here; core-api stays
-- the single owner of the source dictionary. `on conflict do nothing` keeps this migration
-- safe for databases that already received the pilot rows in V3__seed_dictionaries.sql.
--
-- SELECTEL and CLOUD_RU are part of the agreed source-code set even though they are not in
-- the first wave of adapters; their career pages are seeded now so a later adapter only
-- needs code, not another migration.

insert into sources (code, name, base_url)
values ('MTS', 'МТС', 'https://job.mts.ru'),
       ('YANDEX', 'Яндекс', 'https://yandex.ru/jobs'),
       ('VK', 'VK', 'https://team.vk.company'),
       ('SBER', 'Сбер', 'https://rabota.sber.ru'),
       ('TBANK', 'Т-Банк', 'https://www.tbank.ru/career/'),
       ('OZON', 'Ozon', 'https://job.ozon.ru'),
       ('AVITO', 'Авито', 'https://career.avito.ru'),
       ('X5', 'X5 Tech', 'https://rabota.x5.ru/'),
       ('MAGNIT', 'Магнит Tech', 'https://magnit.tech/'),
       ('LAMODA', 'Lamoda', 'https://job-lamoda.ru/'),
       ('RWB', 'RWB (Wildberries)', 'https://rwb.ru/'),
       ('ROSTELECOM', 'Ростелеком', 'https://rt.ru/'),
       ('BEELINE', 'Билайн', 'https://job.beeline.ru/'),
       ('MEGAFON', 'МегаФон', 'https://job.megafon.ru/'),
       ('T2', 'Т2', 'https://t2.ru/career'),
       ('KASPERSKY', 'Лаборатория Касперского', 'https://careers.kaspersky.ru/'),
       ('POSITIVE', 'Positive Technologies', 'https://ptsecurity.com/careers/'),
       ('YADRO', 'YADRO', 'https://careers.yadro.com/'),
       ('SELECTEL', 'Selectel', 'https://selectel.ru/careers/'),
       ('CLOUD_RU', 'Cloud.ru', 'https://cloud.ru/career'),
       ('T1', 'Т1', 'https://career.t1.ru/'),
       ('IBS', 'IBS', 'https://ibs.ru/career/'),
       ('JET', 'Инфосистемы Джет', 'https://jet.su/career/'),
       ('CROC', 'КРОК', 'https://careers.croc.ru/'),
       ('SMART_ENGINES', 'Smart Engines', 'https://smartengines.ru/internship/'),
       ('KODE', 'KODE', 'https://edu.kode.ru/groupinternship'),
       ('NEOFLEX', 'Neoflex', 'https://edu.neoflex.ru/'),
       ('SIMBIRSOFT', 'SimbirSoft', 'https://students.simbirsoft.com/'),
       ('ASTON', 'Aston', 'https://career.astondev.ru/trainee'),
       ('AXENIX', 'Axenix', 'https://axenix.tech/internship'),
       ('NAUMEN', 'Naumen', 'https://www.naumen.ru/career/trainee/'),
       ('POSTGRES_PRO', 'Postgres Professional', 'https://pgstart.ru/'),
       ('YUMONEY', 'ЮMoney', 'https://jobs.yoomoney.ru/'),
       ('KONTUR', 'Контур', 'https://kontur.ru/education/internship'),
       ('SIBUR', 'СИБУР', 'https://career.sibur.ru/internship/'),
       ('GAZPROMBANK', 'Газпромбанк', 'https://www.gazprombank.ru/career/'),
       ('ALFA', 'Альфа-Банк', 'https://job.alfabank.ru/'),
       ('VTB', 'ВТБ', 'https://rabota.vtb.ru/'),
       ('RTLABS', 'РТЛабс', 'https://about.rtlabs.ru/intern'),
       ('DIGITAL_DESIGN', 'Digital Design', 'https://digdes.ru/career/internship'),
       ('1C', '1С', 'https://student.1c.ru/')
on conflict (code) do nothing;
