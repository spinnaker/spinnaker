HAProxy Provider Roadmap — 7 PRs

Overall design: standalone clouddriver-haproxy module (provider id haproxy), one Data Plane API endpoint per account. Frontends map to load balancers, backends to server-group attachments, frontend ACLs/http-request rules to security groups, runtime stats to instance health. Application/cluster identity comes from metadata on HAProxy config objects (spinnaker-app, spinnaker-cluster, spinnaker-stack, spinnaker-detail) with a Frigga name-parse fallback. Each account carries a configurable region label (HAProxy has no regions) and an optional proxmoxAccount link for health correlation.

┌─────┬─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┬──────────────────────┐
│ PR  │                                                                        Scope                                                                        │        Status        │
├─────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────────────┤
│ 1   │ Module skeleton, generated Data Plane v3 SDK (vendored spec + openapi-generator), credentials stack behind haproxy.enabled                          │ ✅ done, uncommitted │
├─────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────────────┤
│ 2   │ Cache keys, HaProxyResourceType, metadata-based namer, frontend + backend caching agents wired into the lifecycle handler                           │ next                 │
├─────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────────────┤
│ 3   │ LoadBalancerProvider — frontends as LBs with binds, default_backend/use_backend relationships to server groups                                      │                      │
├─────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────────────┤
│ 4   │ SecurityGroupProvider — frontend ACLs + http-request allow/deny rules                                                                               │                      │
├─────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────────────┤
│ 5   │ Instance health — runtime stats (server UP/DOWN) correlated to Proxmox instances by IP/metadata                                                     │                      │
├─────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────────────┤
│ 6   │ Write operations — Data Plane transactions (version → transaction → commit, conflict retry): upsert/delete LB, enable/disable servers (maint/ready) │                      │
├─────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────────────┤
│ 7   │ Deck UI package for the provider                                                                                                                    │                      │
└─────┴─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┴──────────────────────┘

Now creating the PR 1 branch off haProxyProvider and committing the work there:
