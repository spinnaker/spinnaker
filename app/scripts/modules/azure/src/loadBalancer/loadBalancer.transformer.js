'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AzureProviderSettings } from '../azure.settings';

export const AZURE_LOADBALANCER_LOADBALANCER_TRANSFORMER = 'spinnaker.azure.loadBalancer.transformer';
export const name = AZURE_LOADBALANCER_LOADBALANCER_TRANSFORMER; // for backwards compatibility
module(AZURE_LOADBALANCER_LOADBALANCER_TRANSFORMER, []).factory('azureLoadBalancerTransformer', [
  '$q',
  function ($q) {
    function normalizeLoadBalancer(loadBalancer) {
      loadBalancer.serverGroups.forEach(function (serverGroup) {
        serverGroup.account = loadBalancer.account;
        serverGroup.region = loadBalancer.region;

        if (serverGroup.detachedInstances) {
          serverGroup.detachedInstances = serverGroup.detachedInstances.map(function (instanceId) {
            return { id: instanceId };
          });
          serverGroup.instances = serverGroup.instances.concat(serverGroup.detachedInstances);
        } else {
          serverGroup.detachedInstances = [];
        }
      });
      const activeServerGroups = _.filter(loadBalancer.serverGroups, { isDisabled: false });
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = _.chain(activeServerGroups).map('instances').flatten().value();
      loadBalancer.detachedInstances = _.chain(activeServerGroups).map('detachedInstances').flatten().value();
      return $q.resolve(loadBalancer);
    }

    function convertLoadBalancerForEditing(loadBalancer) {
      const toEdit = {
        region: loadBalancer.region,
        credentials: loadBalancer.account,
        name: loadBalancer.name,
        stack: loadBalancer.stack,
        detail: loadBalancer.detail,
        vnet: loadBalancer.vnet,
        subnet: loadBalancer.subnet,
        probes: [],
        loadBalancingRules: [],
      };

      if (loadBalancer.elb) {
        const elb = loadBalancer.elb;

        toEdit.securityGroups = elb.securityGroups;
        toEdit.vnet = elb.vnet;

        if (elb.loadBalancingRules) {
          toEdit.loadBalancingRules = elb.loadBalancingRules;
        }

        toEdit.probes = elb.probes;
        if (elb.dnsName && elb.dnsName !== 'dns-not-found') {
          toEdit.dnsName = elb.dnsName.split('.')[0];
        }
      }
      return toEdit;
    }

    function constructNewLoadBalancerTemplate(application) {
      const defaultCredentials = application.defaultCredentials.azure || AzureProviderSettings.defaults.account;
      const defaultRegion = application.defaultRegions.azure || AzureProviderSettings.defaults.region;
      return {
        stack: '',
        detail: 'frontend',
        credentials: defaultCredentials,
        region: defaultRegion,
        cloudProvider: 'azure',
        vnet: null,
        subnet: null,
        probes: [
          {
            probeName: '',
            probeProtocol: 'HTTP',
            probePort: '80',
            probePath: '/',
            probeInterval: 30,
            unhealthyThreshold: 8,
            timeout: 120,
          },
        ],
        securityGroups: [],
        loadBalancingRules: [
          {
            ruleName: '',
            protocol: 'HTTP',
            externalPort: 80,
            backendPort: 80,
            probeName: '',
            persistence: 'None',
            idleTimeout: 4,
          },
        ],
      };
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing,
      constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate,
    };
  },
]);
