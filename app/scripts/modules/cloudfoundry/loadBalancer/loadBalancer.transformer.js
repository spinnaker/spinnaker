'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.loadBalancer.transformer', [
])
  .factory('cfLoadBalancerTransformer', function ($q) {

    function updateHealthCounts(container) {
      var instances = container.instances;
      var serverGroups = container.serverGroups || [container];
      container.healthCounts = {
        upCount: instances.filter(function (instance) {
          return instance.health[0].state === 'InService';
        }).length,
        downCount: instances.filter(function (instance) {
          return instance.health[0].state === 'OutOfService';
        }).length,
        outOfServiceCount: serverGroups.reduce(function (acc, serverGroup) {
          return serverGroup.instances.filter(function (instance) {
            return instance.healthState === 'OutOfService';
          }).length + acc;
        }, 0),
      };
      angular.extend(container, container.healthCounts);
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

    function normalizeLoadBalancer(loadBalancer) {
      return $q.when(loadBalancer); // no-op
    }

    function serverGroupIsInLoadBalancer(serverGroup, loadBalancer) {
      return serverGroup.type === 'cf' &&
        serverGroup.account === loadBalancer.account &&
        serverGroup.region === loadBalancer.region &&
        serverGroup.loadBalancers.indexOf(loadBalancer.name) !== -1;
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      serverGroupIsInLoadBalancer: serverGroupIsInLoadBalancer
    };

  }).name;
