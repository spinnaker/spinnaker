'use strict';


angular.module('deckApp.aws.loadBalancer.transformer.service', [
  'deckApp.settings',
  'deckApp.utils.lodash'
])
  .factory('awsLoadBalancerTransformer', function ( settings, _) {

    function updateHealthCounts(container) {
      var instances = container.instances;
      container.healthCounts = {
        upCount: instances.filter(function (instance) {
          return instance.health[0].state === 'InService';
        }).length,
        downCount: instances.filter(function (instance) {
          return instance.health[0].state === 'OutOfService';
        }).length,
        unknownCount: container.detachedInstances.length,
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

    function normalizeLoadBalancerWithServerGroups(loadBalancer) {
      loadBalancer.serverGroups.forEach(function(serverGroup) {
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
    }

    function serverGroupIsInLoadBalancer(serverGroup, loadBalancer) {
      return serverGroup.type === 'aws' &&
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
        listeners: [],
        name: loadBalancer.name,
        regionZones: loadBalancer.availabilityZones
      };

      if (loadBalancer.elb) {
        var elb = loadBalancer.elb;

        toEdit.securityGroups = elb.securityGroups;
        toEdit.vpcId = elb.vpcid;

        if (elb.listenerDescriptions) {
          toEdit.listeners = elb.listenerDescriptions.map(function (description) {
            var listener = description.listener;
            return {
              internalProtocol: listener.instanceProtocol,
              internalPort: listener.instancePort,
              externalProtocol: listener.protocol,
              externalPort: listener.loadBalancerPort,
              sslCertificateId: listener.sslcertificateId
            };
          });
        }

        if (elb.healthCheck && elb.healthCheck.target) {
          toEdit.healthTimeout = elb.healthCheck.timeout;
          toEdit.healthInterval = elb.healthCheck.interval;
          toEdit.healthyThreshold = elb.healthCheck.healthyThreshold;
          toEdit.unhealthyThreshold = elb.healthCheck.unhealthyThreshold;

          var healthCheck = loadBalancer.elb.healthCheck.target;
          var protocolIndex = healthCheck.indexOf(':'),
            pathIndex = healthCheck.indexOf('/');

          if (pathIndex === -1) {
            pathIndex = healthCheck.length;
          }

          if (protocolIndex !== -1) {
            toEdit.healthCheckProtocol = healthCheck.substring(0, protocolIndex);
            toEdit.healthCheckPort = healthCheck.substring(protocolIndex + 1, pathIndex);
            toEdit.healthCheckPath = healthCheck.substring(pathIndex);
            if (!isNaN(toEdit.healthCheckPort)) {
              toEdit.healthCheckPort = Number(toEdit.healthCheckPort);
            }
          }
        }
      }
      return toEdit;
    }

    function constructNewLoadBalancerTemplate() {
      return {
        stack: '',
        detail: 'frontend',
        credentials: settings.providers.aws.defaults.account,
        region: settings.providers.aws.defaults.region,
        vpcId: null,
        healthCheckProtocol: 'HTTP',
        healthCheckPort: 7001,
        healthCheckPath: '/healthcheck',
        healthTimeout: 5,
        healthInterval: 10,
        healthyThreshold: 10,
        unhealthyThreshold: 2,
        regionZones: [],
        securityGroups: [],
        listeners: [
          {
            internalProtocol: 'HTTP',
            internalPort: 7001,
            externalProtocol: 'HTTP',
            externalPort: 80
          }
        ]
      };
    }

    return {
      normalizeLoadBalancerWithServerGroups: normalizeLoadBalancerWithServerGroups,
      serverGroupIsInLoadBalancer: serverGroupIsInLoadBalancer,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing,
      constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate,
    };

  });
