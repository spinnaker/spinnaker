'use strict';

import _ from 'lodash';

import { AzureProviderSettings } from '../azure.settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.loadBalancer.transformer', [])
  .factory('azureLoadBalancerTransformer', [
    '$q',
    function($q) {
      function normalizeLoadBalancer(loadBalancer) {
        loadBalancer.serverGroups.forEach(function(serverGroup) {
          serverGroup.account = loadBalancer.account;
          serverGroup.region = loadBalancer.region;

          if (serverGroup.detachedInstances) {
            serverGroup.detachedInstances = serverGroup.detachedInstances.map(function(instanceId) {
              return { id: instanceId };
            });
            serverGroup.instances = serverGroup.instances.concat(serverGroup.detachedInstances);
          } else {
            serverGroup.detachedInstances = [];
          }
        });
        var activeServerGroups = _.filter(loadBalancer.serverGroups, { isDisabled: false });
        loadBalancer.provider = loadBalancer.type;
        loadBalancer.instances = _.chain(activeServerGroups)
          .map('instances')
          .flatten()
          .value();
        loadBalancer.detachedInstances = _.chain(activeServerGroups)
          .map('detachedInstances')
          .flatten()
          .value();
        return $q.resolve(loadBalancer);
      }

      function convertLoadBalancerForEditing(loadBalancer) {
        var toEdit = {
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
          var elb = loadBalancer.elb;

          toEdit.securityGroups = elb.securityGroups;
          toEdit.vnet = elb.vnet;

          if (elb.loadBalancingRules) {
            toEdit.loadBalancingRules = elb.loadBalancingRules;
          }

          toEdit.probes = elb.probes;
        }
        return toEdit;
      }

      function constructNewLoadBalancerTemplate(application) {
        var defaultCredentials = application.defaultCredentials.azure || AzureProviderSettings.defaults.account,
          defaultRegion = application.defaultRegion || AzureProviderSettings.defaults.region;
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
              probePort: 'www.bing.com',
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
