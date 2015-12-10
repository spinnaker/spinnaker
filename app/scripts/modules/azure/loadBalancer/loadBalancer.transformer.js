'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.loadBalancer.transformer', [
  require('../../core/utils/lodash.js'),
  require('../vpc/vpc.read.service.js'),
])
  .factory('azureLoadBalancerTransformer', function (settings, _, azureVpcReader) {

    function updateHealthCounts(container) {
      var instances = container.instances;
      var serverGroups = container.serverGroups || [container];
      container.instanceCounts = {
        up: instances.filter(function (instance) {
          return instance.health[0].state === 'InService';
        }).length,
        down: instances.filter(function (instance) {
          return instance.health[0].state === 'OutOfService';
        }).length,
        outOfService: serverGroups.reduce(function (acc, serverGroup) {
          return serverGroup.instances.filter(function (instance) {
            return instance.healthState === 'OutOfService';
          }).length + acc;
        }, 0),
      };
    }

    function transformInstance(instance, loadBalancer) {
      instance.health = instance.health || {};
      instance.provider = loadBalancer.type;
      instance.account = loadBalancer.account;
      instance.region = loadBalancer.region;
      instance.health.type = 'LoadBalancer';
      instance.healthState = instance.health.state ? instance.health.state === 'InService' ? 'Up' : 'Down' : 'OutOfService';
      instance.health = [instance.health];
      instance.loadBalancers = [loadBalancer.name];
    }

    function addVpcNameToLoadBalancer(loadBalancer) {
      return function(vpcs) {
        var matches = vpcs.filter(function(test) {
          return test.id === loadBalancer.vpcId;
        });
        loadBalancer.vpcName = matches.length ? matches[0].name : '';
        return loadBalancer;
      };
    }

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

        serverGroup.instances.forEach(function(instance) {
          transformInstance(instance, loadBalancer);
        });
        updateHealthCounts(serverGroup);
      });
      var activeServerGroups = _.filter(loadBalancer.serverGroups, {isDisabled: false});
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = _(activeServerGroups).pluck('instances').flatten().valueOf();
      loadBalancer.detachedInstances = _(activeServerGroups).pluck('detachedInstances').flatten().valueOf();
      updateHealthCounts(loadBalancer);
      return azureVpcReader.listVpcs().then(addVpcNameToLoadBalancer(loadBalancer));
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
            probePort: 7001,
            probePath: '/healthcheck',
            probeInterval: 10,
            unhealthyThreshold: 2
          }
        ],
        securityGroups: [],
        loadBalancingRules: [
          {
            ruleName: '',
            protocol: 'TCP',
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
