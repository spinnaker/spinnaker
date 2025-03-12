## Configuring SQL store for front50

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
