'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { GCEProviderSettings } from '../gce.settings';

export const GOOGLE_LOADBALANCER_LOADBALANCER_TRANSFORMER = 'spinnaker.gce.loadBalancer.transformer';
export const name = GOOGLE_LOADBALANCER_LOADBALANCER_TRANSFORMER; // for backwards compatibility
module(GOOGLE_LOADBALANCER_LOADBALANCER_TRANSFORMER, []).factory('gceLoadBalancerTransformer', [
  '$q',
  function ($q) {
    function updateHealthCounts(container) {
      const instances = container.instances;
      const serverGroups = container.serverGroups || [container];
      container.instanceCounts = {
        up: instances.filter((instance) => {
          return instance.health[0].state === 'InService';
        }).length,
        down: instances.filter((instance) => {
          return instance.health[0].state === 'OutOfService';
        }).length,
        outOfService: serverGroups.reduce((acc, serverGroup) => {
          return (
            serverGroup.instances.filter((instance) => {
              return instance.healthState === 'OutOfService';
            }).length + acc
          );
        }, 0),
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
      loadBalancer.serverGroups.forEach(function (serverGroup) {
        serverGroup.account = loadBalancer.account;
        if (serverGroup.detachedInstances) {
          serverGroup.detachedInstances = serverGroup.detachedInstances.map(function (instanceId) {
            return { id: instanceId };
          });
          serverGroup.instances = serverGroup.instances.concat(serverGroup.detachedInstances);
        } else {
          serverGroup.detachedInstances = [];
        }

        serverGroup.instances.forEach(function (instance) {
          transformInstance(instance, loadBalancer);
        });
        updateHealthCounts(serverGroup);
      });
      const activeServerGroups = _.filter(loadBalancer.serverGroups, { isDisabled: false });
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = _.chain(activeServerGroups).map('instances').flatten().value();
      loadBalancer.detachedInstances = _.chain(activeServerGroups).map('detachedInstances').flatten().value();
      if (_.get(loadBalancer, 'backendService.healthCheck')) {
        loadBalancer.backendService.healthCheck.timeout = loadBalancer.backendService.healthCheck.timeoutSec;
        loadBalancer.backendService.healthCheck.interval = loadBalancer.backendService.healthCheck.checkIntervalSec;
      }
      updateHealthCounts(loadBalancer);
      return $q.when(loadBalancer);
    }

    function convertLoadBalancerForEditing(loadBalancer) {
      const toEdit = {
        provider: 'gce',
        region: loadBalancer.region,
        credentials: loadBalancer.account,
        listeners: [],
        name: loadBalancer.name,
        regionZones: loadBalancer.availabilityZones,
      };

      if (loadBalancer.elb) {
        const elb = loadBalancer.elb;

        toEdit.vpcId = elb.vpcid;

        if (elb.listenerDescriptions) {
          toEdit.listeners = elb.listenerDescriptions.map(function (description) {
            const listener = description.listener;
            return {
              protocol: listener.protocol,
              portRange: listener.loadBalancerPort,
              healthCheck: elb.healthCheck !== undefined,
            };
          });
        }

        if (elb.healthCheck && elb.healthCheck.target) {
          toEdit.healthTimeout = elb.healthCheck.timeout;
          toEdit.healthInterval = elb.healthCheck.interval;
          toEdit.healthyThreshold = elb.healthCheck.healthyThreshold;
          toEdit.unhealthyThreshold = elb.healthCheck.unhealthyThreshold;

          const healthCheck = loadBalancer.elb.healthCheck.target;
          const protocolIndex = healthCheck.indexOf(':');
          const pathIndex = healthCheck.indexOf('/');

          if (protocolIndex !== -1 && pathIndex !== -1) {
            toEdit.healthCheckProtocol = healthCheck.substring(0, protocolIndex);
            toEdit.healthCheckPort = healthCheck.substring(protocolIndex + 1, pathIndex);
            toEdit.healthCheckPath = healthCheck.substring(pathIndex);
            if (!isNaN(toEdit.healthCheckPort)) {
              toEdit.healthCheckPort = Number(toEdit.healthCheckPort);
            }
          }
        } else {
          toEdit.healthCheckProtocol = 'HTTP';
          toEdit.healthCheckPort = 80;
          toEdit.healthCheckPath = '/';
          toEdit.healthTimeout = 5;
          toEdit.healthInterval = 10;
          toEdit.healthyThreshold = 10;
          toEdit.unhealthyThreshold = 2;
        }

        toEdit.sessionAffinity = loadBalancer.sessionAffinity || 'None';
      }
      return toEdit;
    }

    function constructNewLoadBalancerTemplate() {
      return {
        provider: 'gce',
        stack: '',
        detail: '',
        credentials: GCEProviderSettings.defaults.account,
        region: GCEProviderSettings.defaults.region,
        healthCheckProtocol: 'HTTP',
        healthCheckPort: 80,
        healthCheckPath: '/',
        healthTimeout: 5,
        healthInterval: 10,
        healthyThreshold: 10,
        unhealthyThreshold: 2,
        sessionAffinity: 'NONE',
        regionZones: [],
        listeners: [
          {
            protocol: 'TCP',
            portRange: '8080',
            healthCheck: true,
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
