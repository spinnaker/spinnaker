# kork-core

Provides core libraries for Spinnaker services

# com.netflix.spinnaker.kork.discovery

Offers service discovery integration for Spinnaker services.
Spinnaker services are written to utilize service discovery status to actuate various functionality on and off;
such as work queues, background jobs, and so-on.
A reference implementation can be found in `kork-eureka`.

# Jackson 3 compatibility

Boot-built JSON mappers retain Jackson 2 accessor names, including `getOAuthScopes()`
(`oauthScopes`) and `getvpcId()` (`vpcId`). Set
`spinnaker.jackson.legacy-bean-naming=false` to use Jackson 3 naming after migrating
stored data and clients. Standalone mappers can use `Jackson2AccessorNamingStrategy.Provider`
with `MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX` disabled.

Inject `YamlHelper` and use `yamlFactory()` when building Jackson YAML mappers for
manifests, templates, and Helm documents. It preserves empty values as null,
last-value-wins duplicate keys, and Jackson 2 plain scalar interpretation (`yes`,
`0644`, `0xFF`) while retaining configured YAML parsing limits. Quoted scalars and
String-valued fields retain their text. Set `snakeyaml.yaml11-scalars=false` to opt
out of legacy scalar interpretation. Secrets, plugin resources, and Fiat role files
continue using their existing Jackson 3 YAML factories.
