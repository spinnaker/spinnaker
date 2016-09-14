'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.loadBalancer.transformer', [])
  .factory('openstackLoadBalancerTransformer', function (settings) {
    var defaults = {
      provider: 'openstack',
      account: settings.providers.openstack ? settings.providers.openstack.defaults.account : null,
      stack: '',
      detail: '',
      subnetId: '',
      networkId: '',
      algorithm: 'ROUND_ROBIN',
      healthMonitor: {
        type: 'HTTPS',
        httpMethod: 'GET',
        url: '/healthCheck',
        expectedCodes: [200],
        delay: 10,
        timeout: 1,
        maxRetries: 2
      },
      securityGroups: [],
      listeners: [
        {
          internalPort: 443,
          externalProtocol: 'HTTP',
          externalPort: 443
        }
      ]
    };

    function normalizeLoadBalancer(loadBalancer) {
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = [];

      var healthMonitor = _.get(loadBalancer, 'healthChecks[0]') || loadBalancer.healthMonitor || {};
      delete loadBalancer.healthChecks;
      _(healthMonitor).keys().each(function (k) {
        if (healthMonitor[k] === null) {
          delete healthMonitor[k];
        }
      });

      loadBalancer.healthMonitor = _.defaults(healthMonitor, defaults.healthMonitor);

      return loadBalancer;
    }

    function serverGroupIsInLoadBalancer(serverGroup, loadBalancer) {
      return serverGroup.type === 'openstack' &&
        serverGroup.account === loadBalancer.account &&
        serverGroup.region === loadBalancer.region &&
        serverGroup.loadBalancers.indexOf(loadBalancer.name) !== -1;
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
      serverGroupIsInLoadBalancer: serverGroupIsInLoadBalancer,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing
    };
  });
