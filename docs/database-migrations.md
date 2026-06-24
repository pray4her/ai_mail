# Database Migrations

Flyway is the authoritative migration mechanism for the MySQL schema. New relational database changes must be added as versioned SQL files under `ai_mail_src/src/main/resources/db/migration`.

## Migration Files

- Use Flyway default naming: `V<version>__<description>.sql`.
- Do not edit, reorder, or rename migrations that may have been applied in any shared environment.
- Add a new forward migration for every schema change or small deterministic data correction.
- Keep Elasticsearch mappings, MinIO buckets, embedding rebuilds, and large backfills outside Flyway.
- Avoid `DROP TABLE` and destructive changes in baseline or compatibility migrations. Split destructive changes into compatible release phases.
- Write normal migrations deterministically. Do not use `IF NOT EXISTS` by default; drift should fail visibly.
- Use `information_schema` guards only for adoption migrations that must tolerate known historical schema differences.

## Current Baseline

- `V1__baseline_schema.sql` creates the current intended MySQL schema for empty databases.
- `V2__adopt_history_sync_schema.sql` adopts historical databases that were created from the old SQL assets and need history-sync columns, indexes, and missing current tables.
- `mail.sql`, `ai_mail_src/src/main/resources/schema.sql`, and `db_upgrade_history_sync.sql` are historical references or temporary compatibility assets. They are not the source of truth for new schema work.

## Development

Start local infrastructure with:

```bash
docker compose -f dev/docker-compose.yml up -d mysql
```

Then either start the backend, which runs Flyway automatically outside the `prod` profile:

```bash
mvn -f ai_mail_src/pom.xml spring-boot:run
```

or run Flyway explicitly:

```bash
mvn -f ai_mail_src/pom.xml flyway:validate flyway:migrate
```

The Maven Flyway plugin uses the same default local database shape as the application: `localhost:3306/mail`, `root`, `123456`. Override with Maven properties when needed:

```bash
mvn -f ai_mail_src/pom.xml flyway:migrate -Dflyway.db.host=127.0.0.1 -Dflyway.db.port=3306 -Dflyway.db.name=mail -Dflyway.db.username=root -Dflyway.db.password=123456
```

## Existing Database Adoption

For a database that already has the historical schema but no `flyway_schema_history` table:

1. Back up the database.
2. Verify that the database is the expected AI Mail MySQL schema.
3. Run Flyway with `baselineOnMigrate=true` and `baselineVersion=1` once, so Flyway records the existing schema as version `1`.
4. Run `migrate` so `V2__adopt_history_sync_schema.sql` applies the guarded compatibility changes.
5. Turn `baselineOnMigrate` back off.

Do not use the adoption path for empty databases. Empty databases should run from `V1`.

## Production Release

Production application startup must not perform schema-changing migrations implicitly. Use the `prod` profile or `APP_FLYWAY_ENABLED=false` for the application process.

Release order:

1. Deploy or stage the artifact containing new migrations.
2. Run `mvn -f ai_mail_src/pom.xml flyway:validate` against production.
3. Run `mvn -f ai_mail_src/pom.xml flyway:migrate` as an explicit release step.
4. Start or roll the application after migration succeeds.

If validation or migration fails, stop the release. Fix with a new forward migration, or use Flyway repair only after a deliberate operator review of metadata drift. Do not edit an applied migration script.
