# HAProxy Data Plane API specification

`dataplane_spec.yaml` is the official HAProxy Data Plane API OpenAPI 3.0 specification, vendored
from the upstream repository. The Java SDK used by this module is generated from it at build time
via the `openApiGenerate` Gradle task (see `clouddriver-haproxy.gradle`) — no client code is
committed to this repository.

- Source: https://github.com/haproxytech/dataplaneapi (`specification/build/dataplane_spec.yaml`)
- Upstream commit: `f314b6be48a65322bdd74153555372fda5e07a88` (2026-07-01)
- API version: 3.4

To update the spec:

```bash
curl -sfL https://raw.githubusercontent.com/haproxytech/dataplaneapi/master/specification/build/dataplane_spec.yaml \
  -o dataplane_spec.yaml
```

then rebuild and update the commit/version noted above.
