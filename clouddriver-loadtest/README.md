# clouddriver-loadtest

This module is a work in progress for testing the reliability and
scale characteristics of clouddriver.

It is currently intended for ad-hoc execution and is not run as part of CI.

## Generate dataset for `fetchApplications` scenario

If you end up running any load for the `fetchApplications` scenario, you'll need
to provide a list of applications. To get all applications in the format the test
expects, you can query Front50:

```bash
curl http://front50-url/v2/applications | jq -s '[.[][] | select(.name) | {name: .name}]'  > /tmp/applications.json
```

If you'd like to filter the list of applications matching a pattern:

```bash
curl http://front50-url/v2/applications | jq -s '[.[][] | select(.name | contains("HELLOTHISISDOG")) | {name: .name}]'  > /tmp/applications.json
```
