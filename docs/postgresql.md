# PostgreSQL profile for an imported database

The application supports the existing MySQL database and a separate PostgreSQL
copy. Set `DB_PROFILE=postgresql` in the local, ignored `.env` file and configure
`POSTGRES_DB_URL`, `POSTGRES_DB_USERNAME`, and `POSTGRES_DB_PASSWORD` there.
The PostgreSQL JDBC URL should include `stringtype=unspecified` so JDBC string
parameters can be used with PostgreSQL JSON columns. Never commit credentials.
Set `DB_PROFILE=mysql` (or omit it) to use the existing `DB_URL`, `DB_USERNAME`,
and `DB_PASSWORD` settings instead. The databases do not synchronize changes.

The PostgreSQL migration location is `db/postgresql`, and its history table is
`flyway_postgresql_history`. It is designed for a database copied from this
application's MySQL schema at Flyway version 59. Flyway baselines that imported,
non-empty schema at version 59 and then applies PostgreSQL-specific migrations.
The copied MySQL `flyway_schema_history` is retained as historical data, not as
the PostgreSQL migration history. This profile does **not** provision a new empty
database, and a different source schema version needs a separate migration plan.

The AI assistant remains disabled in the PostgreSQL profile: its separate
read-only connection and metadata/SQL generation currently target MySQL. Do not
point it at a PostgreSQL administrator or application owner account. Configure a
dedicated read-only PostgreSQL account and port and test the assistant before
enabling it. PostgreSQL and MySQL may differ in text comparison and sorting, so
validate imported workflows and results before relying on them operationally.

The PostgreSQL integration tests run only when `POSTGRES_TEST_URL`,
`POSTGRES_TEST_USER`, and `POSTGRES_TEST_PASSWORD` are provided in the process
environment. Use an isolated test database where possible; the SQL test uses a
temporary table, and the repository test uses synthetic data in a transaction
that is rolled back.
