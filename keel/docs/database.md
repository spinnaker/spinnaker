# Database

Keel assumes a SQL database backend.
The codebase uses the [jOOQ](https://jooq.org) library for generating and executing SQL queries.
Internally, Netflix uses Amazon Aurora MySQL to back keel, so that's the most battle-tested back-end.

## Transaction isolation

Keel defaults to READ COMMITTED transaction isolation level, which is weaker than the MySQL default of REPEATABLE READ.
Keel inherits this setting from [Kork](http://github.com/spinnaker/kork), in [DefaultSqlConfiguration.dataSourceConnectionProvider](https://github.com/spinnaker/kork/blob/master/kork-sql/src/main/kotlin/com/netflix/spinnaker/kork/sql/config/DefaultSqlConfiguration.kt#L146-L154)


## Migrations

Keel uses [liquibase](https://www.liquibase.org/) for database migrations.
We use the YAML representation for specifying migrations.

Migration files are in keel-sql/src/main/resources/db/changelog.

## Conventions

### Table names

Table names use snake_case.
Table names are (usually) singular (e.g., `delivery_config` not `delivery_configs`).

### Primary key column

The primary key column is named `uid`, and is a 26-character string.
Like the other Spinnaker apps, keel uses [ULIDs](https://github.com/ulid/spec) for primary key ids.
A ULID is canonically represented as a 26 character string, so the primary key column definition looks like this in the liquibase migrations:

```yaml
- createTable:
    tableName: ...
        - column:
          name: uid
          type: char(26)
          constraints:
          nullable: false
```


To generate a ULID in code:

```kotlin
import com.netflix.spinnaker.keel.core.api.randomUID

val uid = randomUID().toString()
```

### Column ordering

The biggest columns should be farthest right.

