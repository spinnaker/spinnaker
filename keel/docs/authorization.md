## Keel API authorization

### Overview
Keel now authorizes requests with the shared
kork authorization libraries (`kork-authz`/`kork-security`): inbound identity tokens are verified by the
verifier-only chain in `com.netflix.spinnaker.config.SecurityConfiguration`, the caller's roles are read from the
verified token (no remote authorization call), and per-resource decisions are delegated to the kork
`PolicyDecisionPointPermissionEvaluator`.

Authorization is gated on the master switch `authz.enabled` (default `false` = disabled), consistent with the
other migrated services and modeled on the legacy `services.fiat.enabled`. While disabled, keel allows access and
falls back to the unsigned `X-SPINNAKER-USER` header; flipping the flag to `true` enables token-driven enforcement.
Note that keel does not own the ACLs for applications, cloud accounts, or service accounts (those resources live in
other services), so when enforcing,
non-admin decisions for those resources rely on token-sourced roles only.

#### Accepting an API token directly
Keel only understands the signed identity token, not the opaque `spk_` API token. When
`authz.api-token-exchange.enabled=true` (the Gate address is the standard
`services.gate.baseUrl`), a `spk_` token presented directly to keel (e.g. via port-forward, in `X-Spinnaker-Token` or
`Authorization: Bearer spk_…`) is exchanged once at Gate's `/auth/apiTokens/exchange` endpoint for
the signed token Gate would have minted, and then verified through the normal chain — so the result
is identical to a request proxied through Gate. See `gate/README.md` for the full configuration.

To simplify the implementation of authorization checks and avoid redundant code, we provide a helper class,
`com.netflix.spinnaker.keel.rest.AuthorizationSupport`, with high-level authorization functions that wrap the standard
`PermissionEvaluator.hasPermission()` function. 

Access to APIs is granted based on one or more of the following permission checks:
 - The caller (as identified by the X-SPINNAKER-USER request header, or X509 client certificate) has access to the
   application associated with the delivery config, with the right permission level (READ or WRITE) for the API.
   This check is provided by the `AuthorizationSupport.hasApplicationPermission()` function.
 - The caller has  access to the service account associated with the delivery config. This check is provided by the
   `AuthorizationSupport.hasServiceAccountAccess()` function. 
 - The caller has access to the cloud account associated with each applicable resource (implementors of the `Locatable`
   interface) within the delivery config. This check is provided by the `AuthorizationSupport.hasCloudAccountPermission()`
   function.

The authorization functions in `AuthorizationSupport` extract the necessary information from keel's database, based on 
request parameters, so that the appropriate permission checks can be carried out by the kork
`PolicyDecisionPointPermissionEvaluator`. 

For example, consider the API to read a delivery config: `GET /delivery-configs/{name}`. We check that the caller has 
`READ` permission to the application:
```
authorizationSupport.hasApplicationPermission('READ', 'DELIVERY_CONFIG', #name)
```
...and `READ` permission to the required cloud accounts:
```
authorizationSupport.hasCloudAccountPermission('READ', 'DELIVERY_CONFIG', #name)
```
In order to check the cloud account permissions, we look up the delivery config from the database by name, then iterate 
each of its resources to call `permissionEvaluator.hasPermission(auth, account, "ACCOUNT", "READ")`.

 ### What authorization checks to use when 
Use the following guidelines to determine what level of check is required for each API:
  - Operations that read data from keel’s database exclusively should require READ access to the application.
  - Operations that read data from keel’s database and from cloud infrastructure should require READ access to
    the application AND, for each resource returned, READ access to the corresponding cloud account.
  - Operations that trigger storing data in keel’s database, but do NOT trigger infrastructure changes, should
    require WRITE access to the application.
  - Operations that trigger actuation, whereby the service account will take action on behalf of the original caller,
    should check the caller has access to the service account, in addition to WRITE access to the application.

### How to add authorization checks
We leverage the `@PreAuthorize` annotation from Spring Security to enforce authorization checks in `RestController`
functions exposing APIs. The value of this annotation is a SpEL expression that has access to the Spring Context,
so we reference the single Bean of type `AuthorizationSupport` in the context to call the appropriate authorization
checks for that endpoint.

Here is the example for `GET /delivery-configs/{name}` again:
```kotlin
  @GetMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('READ', 'DELIVERY_CONFIG', #name)
    and @authorizationSupport.hasCloudAccountPermission('READ', 'DELIVERY_CONFIG', #name)"""
  )
  fun get(@PathVariable("name") name: String): DeliveryConfig =
    repository.getDeliveryConfig(name)
```

### How to test authorization checks
Always add tests for all authorized controller functions. To make this easier, there are a few helper functions
provided by `AuthorizationTestSupport` which allow you to mock the authorization checks to pass or fail as needed.

Again, here are the tests for the `DeliveryController` request function above:
```kotlin
  context("GET /delivery-configs") {
    context("with no READ access to application") {
      before {
        authorizationSupport.denyApplicationAccess(READ, DELIVERY_CONFIG)
        authorizationSupport.allowCloudAccountAccess(READ, DELIVERY_CONFIG)
      }
      test("request is forbidden") {
        val request = get("/delivery-configs/${deliveryConfig.name}")
          .accept(MediaType.APPLICATION_JSON_VALUE)
          .header("X-SPINNAKER-USER", "keel@keel.io")

        mvc.perform(request).andExpect(status().isForbidden)
      }
    }
    context("with no READ access to cloud account") {
      before {
        authorizationSupport.denyCloudAccountAccess(READ, DELIVERY_CONFIG)
        authorizationSupport.allowApplicationAccess(READ, DELIVERY_CONFIG)
      }
      test("request is forbidden") {
        val request = get("/delivery-configs/${deliveryConfig.name}")
          .accept(MediaType.APPLICATION_JSON_VALUE)
          .header("X-SPINNAKER-USER", "keel@keel.io")

        mvc.perform(request).andExpect(status().isForbidden)
      }
    }
  }
```
