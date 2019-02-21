'use strict';

import _ from 'lodash';

import { OpenStackProviderSettings } from '../openstack.settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.openstack.loadBalancer.transformer', [])
  .factory('openstackLoadBalancerTransformer', [
    '$q',
    function($q) {
      var defaults = {
        provider: 'openstack',
        account: OpenStackProviderSettings.defaults.account,
        stack: '',
        detail: '',
        subnetId: '',
        networkId: '',
        algorithm: 'ROUND_ROBIN',
        healthMonitor: {
          type: 'HTTP',
          httpMethod: 'GET',
          url: '/healthCheck',
          expectedCodes: [200],
          delay: 10,
          timeout: 1,
          maxRetries: 2,
        },
        securityGroups: [],
        listeners: [
          {
            internalPort: 80,
            externalProtocol: 'HTTP',
            externalPort: 80,
          },
        ],
      };

      function updateHealthCounts(container) {
        var healths = container.healths;
        container.instanceCounts = {
          up: healths.filter(function(instance) {
            return instance.lbHealthSummaries[0].healthState === 'InService';
          }).length,
          down: healths.filter(function(instance) {
            return instance.lbHealthSummaries[0].healthState === 'OutOfService';
          }).length,
        };
      }

      function updateServerGroupHealthCounts(container) {
        var instances = container.instances;
        container.instanceCounts = {
          up: instances.filter(function(instance) {
            return instance.health[0].state === 'Up';
          }).length,
          down: instances.filter(function(instance) {
            return instance.health[0].state === 'Down';
          }).length,
        };
      }

      function transformInstance(instance, loadBalancer) {
        instance.health = instance.health || {};
        instance.provider = loadBalancer.type;
        instance.account = loadBalancer.account;
        instance.region = loadBalancer.region;
        instance.health.type = 'LoadBalancer';
        instance.healthState = instance.health.state
          ? instance.health.state === 'InService'
            ? 'Up'
            : 'Down'
          : 'OutOfService';
        instance.health = [instance.health];
        instance.loadBalancers = [loadBalancer.name];
      }

      function normalizeLoadBalancer(loadBalancer) {
        loadBalancer.serverGroups.forEach(function(serverGroup) {
          serverGroup.account = loadBalancer.account;
          if (serverGroup.detachedInstances) {
            serverGroup.detachedInstances = serverGroup.detachedInstances.map(function(instanceId) {
              return { id: instanceId };
            });
            serverGroup.instances = serverGroup.instances.concat(serverGroup.detachedInstances);
          } else {
            serverGroup.detachedInstances = [];
          }

          serverGroup.instances.forEach(function(instance) {
            transformInstance(instance, loadBalancer);
          });
          updateServerGroupHealthCounts(serverGroup);
        });

        loadBalancer.provider = loadBalancer.type;
        loadBalancer.instances = [];

        var healthMonitor = _.get(loadBalancer, 'healthChecks[0]') || loadBalancer.healthMonitor || {};
        delete loadBalancer.healthChecks;
        _.chain(healthMonitor)
          .keys()
          .each(function(k) {
            if (healthMonitor[k] === null) {
              delete healthMonitor[k];
            }
          });

        loadBalancer.healthMonitor = _.defaults(healthMonitor, defaults.healthMonitor);

        updateHealthCounts(loadBalancer);
        return $q.resolve(loadBalancer);
      }

      function constructNewLoadBalancerTemplate() {
        return angular.copy(defaults);
      }

      function convertLoadBalancerForEditing(loadBalancer) {
        _.defaults(loadBalancer, defaults);
        return loadBalancer;
      }

      return {
        normalizeLoadBalancer: normalizeLoadBalancer,
        constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate,
        convertLoadBalancerForEditing: convertLoadBalancerForEditing,
      };
    },
  ]);
