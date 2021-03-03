## Configuring SQL store for fiat

#### MySQL:

```yaml
sql:
  enabled: true
  baseUrl: jdbc:mysql://localhost:3306/fiat
  connectionPools:
    default:
      jdbcUrl: ${sql.baseUrl}?useSSL=false&serverTimezone=UTC
      user: 
      password:
  migration:
    jdbcUrl: ${sql.baseUrl}?useSSL=false&serverTimezone=UTC
    user: 
    password:

permissionsRepository:
  redis:
    enabled: false
  sql:
    enabled: true
```

#### PostgreSQL:
```yaml
sql:
  enabled: true
  baseUrl: jdbc:postgresql://localhost:5432/fiat
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

permissionsRepository:
  redis:
    enabled: false
  sql:
    enabled: true
```
