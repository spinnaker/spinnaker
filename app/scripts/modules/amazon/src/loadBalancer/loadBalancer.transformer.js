'use strict';

const angular = require('angular');
import _ from 'lodash';

import { AWSProviderSettings } from '../aws.settings';
import { VPC_READ_SERVICE } from '../vpc/vpc.read.service';

module.exports = angular.module('spinnaker.aws.loadBalancer.transformer', [
  VPC_READ_SERVICE,
])
  .factory('awsLoadBalancerTransformer', function (vpcReader) {

    function updateHealthCounts(container) {
      const instances = container.instances;
      const serverGroups = container.serverGroups || [container];
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

    function transformInstance(instance, provider, account, region) {
      instance.health = instance.health || {};
      if (instance.health.state === 'healthy') {
        // Target groups use 'healthy' instead of 'InService' and a lot of deck expects InService
        // to surface health in the UI; just set it as InService since we don't really care the
        // specific state name... yet
        instance.health.state = 'InService';
      }
      instance.provider = provider;
      instance.account = account;
      instance.region = region;
      instance.healthState = instance.health.state ? instance.health.state === 'InService' ? 'Up' : 'Down' : 'OutOfService';
      instance.health = [instance.health];
    }

    function addVpcNameToContainer(container) {
      return (vpcs) => {
        const match = vpcs.find((test) => test.id === container.vpcId);
        container.vpcName = match ? match.name : '';
        return container;
      };
    }

    function normalizeServerGroups(serverGroups, container, containerType, healthType) {
      serverGroups.forEach((serverGroup) => {
        serverGroup.account = container.account;
        serverGroup.region = container.region;
        if (serverGroup.detachedInstances) {
          serverGroup.detachedInstances = serverGroup.detachedInstances.map(function(instanceId) {
            return { id: instanceId };
          });
          serverGroup.instances = serverGroup.instances.concat(serverGroup.detachedInstances);
        } else {
          serverGroup.detachedInstances = [];
        }

        serverGroup.instances.forEach(function(instance) {
          transformInstance(instance, container.type, container.account, container.region);
          instance[containerType] = [container.name];
          instance.health.type = healthType;
        });
        updateHealthCounts(serverGroup);
      });
    }

    function normalizeTargetGroup(targetGroup) {
      normalizeServerGroups(targetGroup.serverGroups, targetGroup, 'targetGroups', 'TargetGroup');

      const activeServerGroups = _.filter(targetGroup.serverGroups, {isDisabled: false});
      targetGroup.provider = targetGroup.type;
      targetGroup.instances = _.chain(activeServerGroups).map('instances').flatten().value();
      targetGroup.detachedInstances = _.chain(activeServerGroups).map('detachedInstances').flatten().value();
      updateHealthCounts(targetGroup);

      return vpcReader.listVpcs().then(addVpcNameToContainer(targetGroup));
    }

    function normalizeLoadBalancer(loadBalancer) {
      normalizeServerGroups(loadBalancer.serverGroups, loadBalancer, 'loadBalancers', 'LoadBalancer');

      let serverGroups = loadBalancer.serverGroups;
      if (loadBalancer.targetGroups) {
        loadBalancer.targetGroups.forEach((targetGroup) => normalizeTargetGroup(targetGroup, loadBalancer));
        serverGroups = _.flatten(_.map(loadBalancer.targetGroups, 'serverGroups'));
      }

      const activeServerGroups = _.filter(serverGroups, {isDisabled: false});
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = _.chain(activeServerGroups).map('instances').flatten().value();
      loadBalancer.detachedInstances = _.chain(activeServerGroups).map('detachedInstances').flatten().value();
      updateHealthCounts(loadBalancer);
      return vpcReader.listVpcs().then(addVpcNameToContainer(loadBalancer));
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
        toEdit.vpcId = elb.vpcid || elb.vpcId;

        if (elb.listenerDescriptions) {
          toEdit.listeners = elb.listenerDescriptions.map((description) => {
            var listener = description.listener;
            if (listener.sslcertificateId) {
              var splitCertificateId = listener.sslcertificateId.split('/');
              listener.sslcertificateId = splitCertificateId[1];
              listener.sslCertificateType = splitCertificateId[0].split(':')[2];
            }
            return {
              internalProtocol: listener.instanceProtocol,
              internalPort: listener.instancePort,
              externalProtocol: listener.protocol,
              externalPort: listener.loadBalancerPort,
              sslCertificateId: listener.sslcertificateId,
              sslCertificateName: listener.sslcertificateId,
              sslCertificateType: listener.sslCertificateType
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

    function constructNewLoadBalancerTemplate(application) {
      var defaultCredentials = application.defaultCredentials.aws || AWSProviderSettings.defaults.account,
          defaultRegion = application.defaultRegions.aws || AWSProviderSettings.defaults.region,
          defaultSubnetType = AWSProviderSettings.defaults.subnetType;
      return {
        stack: '',
        detail: '',
        isInternal: false,
        credentials: defaultCredentials,
        region: defaultRegion,
        vpcId: null,
        subnetType: defaultSubnetType,
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
            externalPort: 80,
            sslCertificateType: 'iam'
          }
        ]
      };
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing,
      constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate,
    };

  });
