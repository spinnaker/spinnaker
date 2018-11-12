Newrelic Backend
====

Newrelic integration goes through the insights API by using a TIMESERIES-NRQL-query. The following lines explain, how to configure Kayenta accordingly.

1. In kayenta/kayenta-web/config/kayenta.yml enable the newrelic-block. Specificially, apiKey and applicationKey need to be set.
1. Create a canary-config either as part of an adhoc-canary or a separate one. See scratch/newrelic_canary_config.json for an example. All statements are basically NRQL parts. The select-statement is mandatory and is directly used as NRQL-SELECT. The q statement is optional and will be placed behind the "WHERE"-part of the query.
1. Prepare the executionRequest. An example can be seen here: scratch/newrelic_adhoc_canary.json. The important part is the "scope"-value. It will be concatenated to the "WHERE"-part of the NRQL query with extendedScopeParam _scope_key as key. This needs to differentiate between baseline and experiment scope.

We recommend to manually set the step size to a value between 1 second and an upper value limit the maximum resolution because Insights-API has an upper limit for TIMESERIES queries.
