# Configuring Kayenta

The best way to configure Kayenta if you are not using [Halyard](https://github.com/spinnaker/halyard) is to copy [kayenta.yml](../kayenta-web/config/kayenta.yml) and edit it.

Please note that you need the following:

## At least one Configured Object Store

Kayenta uses the object store to save its results, so this is essentially Kayenta's data store.

## At least one Configured Metrics Store

Metric stores are the actual integration of a metric source (Prometheus, Stackdriver, Atlas, SignalFx, etc) into Kayenta.

## At least one Configured Configuration Store

Configuration stores are where Kayenta will save the canary configuration it is instructed to save. The Object and Config store can be the same.

## Examples

- See the reference [kayenta.yml](../kayenta-web/config/kayenta.yml) for the available config options.
- See the [SignalFx Integration Test Config](../kayenta-signalfx/src/integration-test/resources/config/kayenta.yml) for a working example.
