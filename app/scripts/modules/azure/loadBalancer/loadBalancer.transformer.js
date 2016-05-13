'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.loadBalancer.transformer', [
])
  .factory('azureLoadBalancerTransformer', function (settings) {

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
      var activeServerGroups = _.filter(loadBalancer.serverGroups, {isDisabled: false});
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = _(activeServerGroups).pluck('instances').flatten().valueOf();
      loadBalancer.detachedInstances = _(activeServerGroups).pluck('detachedInstances').flatten().valueOf();
      return loadBalancer;
    }

    function serverGroupIsInLoadBalancer(serverGroup, loadBalancer) {
      return serverGroup.type === 'azure' &&
        serverGroup.account === loadBalancer.account &&
        serverGroup.region === loadBalancer.region &&
        (typeof loadBalancer.vpcId === 'undefined' || serverGroup.vpcId === loadBalancer.vpcId) &&
        serverGroup.loadBalancers.indexOf(loadBalancer.name) !== -1;
    }

    function convertLoadBalancerForEditing(loadBalancer) {
      var toEdit = {
        editMode: true,
        region: loadBalancer.region,
        credentials: loadBalancer.account,
        loadBalancingRules: [],
        name: loadBalancer.name,
        stack: loadBalancer.stack,
        detail: loadBalancer.detail,
        probes: []
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
      var defaultCredentials = application.defaultCredentials || settings.providers.azure.defaults.account,
          defaultRegion = application.defaultRegion || settings.providers.azure.defaults.region;
      return {
        stack: '',
        detail: 'frontend',
        credentials: defaultCredentials,
        region: defaultRegion,
        cloudProvider: 'azure',
        vnet: null,
        probes: [
          {
            probeName: '',
            probeProtocol: 'HTTP',
            probePort: 'www.bing.com',
            probePath: '/',
            probeInterval: 30,
            unhealthyThreshold: 8,
			timeout: 120
          }
        ],
        securityGroups: [],
        loadBalancingRules: [
          {
            ruleName: '',
            protocol: 'HTTP',
            externalPort: 80,
            backendPort: 8080,
            probeName: '',
            persistence: 'None',
            idleTimeout: 4,
          }
        ],
      };
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      serverGroupIsInLoadBalancer: serverGroupIsInLoadBalancer,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing,
      constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate,
    };

  });
