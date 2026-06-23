# Regional External TCP/UDP Network Load Balancer

Adds first-class GCP support for regional external passthrough TCP/UDP Network Load Balancers via a new `REGIONAL_EXTERNAL_NETWORK` type.

This type models a regional forwarding rule with `loadBalancingScheme=EXTERNAL`, no target proxy, and a direct regional backend service. The backend service uses regional health checks and `CONNECTION` balancing mode, supports TCP/UDP discrete `ports`, external address selection, `networkTier`, and passthrough session affinity values.

The change also tightens existing INTERNAL passthrough ownership checks. INTERNAL cache, lifecycle, and CRUD paths now require `loadBalancingScheme=INTERNAL`, `target=null`, and backend-service scheme rechecks so same-named EXTERNAL passthrough resources are not cached or mutated as INTERNAL load balancers.

INTERNAL load balancer deletion now also honors the existing `deleteHealthChecks` flag before reading/deleting the health check, matching the behavior expected by the delete modal for backend-service based L4 load balancers.

Out of scope for this first pass: `L3_DEFAULT`/all-ports, IPv6, PSC, regional external proxy NLBs, cross-region, and Traffic Director. Fragmented UDP affinity still needs all-ports plus `connectionTrackingPolicy=PER_SESSION`, so it remains a documented limitation for this scoped implementation.

Targeted coverage added for the new cache/readback model, CRUD validators and operations, backend attach/detach helpers, Deck create/details flows, and server-group default attachment paths.
