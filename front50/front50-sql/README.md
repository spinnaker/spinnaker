## Configuring SQL store for front50

> **Note:** SQL is the only supported Front50 metadata storage backend. The S3, GCS, Redis,
> Azure, Oracle and Swift backends were removed in Spinnaker 2027.0.0; see the
> [Front50 SQL setup guide](https://spinnaker.io/docs/setup/productionize/persistence/front50-sql/)
> for migration steps. S3 plugin-binary storage (`spinnaker.s3.plugin-storage`) and
> Orca's artifact store are not affected.

#### MySQL:

```yaml
sql:
  enabled: true
  baseUrl: jdbc:mysql://localhost:3306/front50
  connectionPools:
    default:
      jdbcUrl: ${sql.baseUrl}?useSSL=false&serverTimezone=UTC
      user: 
      password:
  migration:
    jdbcUrl: ${sql.baseUrl}?useSSL=false&serverTimezone=UTC
    user: 
    password:
```

#### PostgreSQL:
```yaml
sql:
  enabled: true
  baseUrl: jdbc:postgresql://localhost:5432/front50
  connectionPools:
    default:
      jdbcUrl: ${sql.baseUrl}
      dialect: POSTGRES
      user: 
      password:
  migration:
    jdbcUrl: ${sql.baseUrl}
    user: 
    password:
```
