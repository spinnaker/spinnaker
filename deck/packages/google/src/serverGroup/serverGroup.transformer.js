'use strict';

import { defaults, get } from 'lodash';

import { GceHttpLoadBalancerUtils } from '../loadBalancer/httpLoadBalancerUtils.service';

export const GOOGLE_SERVERGROUP_SERVERGROUP_TRANSFORMER = 'spinnaker.gce.serverGroup.transformer';
export const name = GOOGLE_SERVERGROUP_SERVERGROUP_TRANSFORMER; // for backwards compatibility
export class GceServerGroupTransformer {
  constructor() {
    const gceHttpLoadBalancerUtils = new GceHttpLoadBalancerUtils();
    function normalizeServerGroup(serverGroup, application) {
      return application
        .getDataSource('loadBalancers')
        .ready()
        .then(() => {
          if (serverGroup.loadBalancers) {
            // At this point, the HTTP(S) load balancers have been normalized (listener names mapped to URL map names).
            // Our server groups' lists of load balancer names still need to make this mapping.
            serverGroup.loadBalancers = gceHttpLoadBalancerUtils.normalizeLoadBalancerNamesForAccount(
              serverGroup.loadBalancers,
              serverGroup.account,
              application.getDataSource('loadBalancers').data,
            );
          }
          return serverGroup;
        });
    }

    function getAvailabilityZones(base) {
      if (base.zone) {
        return [base.zone];
      }

      return (
        [
          get(base, 'backingData.filtered.truncatedZones'),
          get(base, ['availabilityZones', base.region]),
          get(base, 'distributionPolicy.zones'),
        ].find((zones) => Array.isArray(zones) && zones.length) || []
      );
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use defaults to avoid copying the backingData, which is huge and expensive to copy over
      const command = defaults({ backingData: [], viewState: [] }, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }
      // We took this approach to avoid a breaking change to existing pipelines.
      command.disableTraffic = !command.enableTraffic;
      command.cloudProvider = 'gce';
      command.availabilityZones = {};
      command.availabilityZones[command.region] = getAvailabilityZones(base);
      command.account = command.credentials;
      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;
      delete command.implicitSecurityGroups;
      delete command.enableTraffic;
      delete command.providerType;
      delete command.enableAutoHealing;
      delete command.partnerMetadata;

      if (command.autoHealingPolicy) {
        command.autoHealingPolicy = ['healthCheck', 'healthCheckKind', 'healthCheckUrl', 'initialDelaySec'].reduce(
          (supportedPolicy, field) => {
            if (command.autoHealingPolicy[field] !== undefined) {
              supportedPolicy[field] = command.autoHealingPolicy[field];
            }
            return supportedPolicy;
          },
          {},
        );
      }

      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };
  }
}
