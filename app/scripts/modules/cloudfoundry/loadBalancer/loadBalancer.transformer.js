'use strict';

import _ from 'lodash';

import {CloudFoundryProviderSettings} from '../cf.settings';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.loadBalancer.transformer', [])
  .factory('cfLoadBalancerTransformer', function ($q) {

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
      loadBalancer.instances = _.chain(activeServerGroups).map('instances').flatten().value();
      loadBalancer.detachedInstances = _.chain(activeServerGroups).map('detachedInstances').flatten().value();
      updateHealthCounts(loadBalancer);
      return $q.when(loadBalancer);
    }

    function constructNewLoadBalancerTemplate() {
      return {
        cloudProvider: 'cf',
        stack: '',
        detail: '',
        credentials: CloudFoundryProviderSettings ? CloudFoundryProviderSettings.defaults.account : null,
        region: CloudFoundryProviderSettings ? CloudFoundryProviderSettings.defaults.region : null,
      };
    }

    function convertLoadBalancerForEditing(loadBalancer) {
      return {
        cloudProvider: 'cf',
        stack: loadBalancer.stack,
        detail: loadBalancer.detail,
        credentials: loadBalancer.credentials,
        region: loadBalancer.region,
      };
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing,
    };

  });
