For starting manually gitea container to see how it works, you can use this `docker-compose.yml` file:

```yaml
version: '2'
services:
  web:
    image: gitea/gitea:1.12.6
    ports:
      - "3000:3000"
      - "22:22"
```

And run `docker-compose up`
