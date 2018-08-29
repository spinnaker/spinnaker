'use strict';

const angular = require('angular');
import _ from 'lodash';

import { AccountService, INSTANCE_TYPE_SERVICE, NameUtils } from '@spinnaker/core';

import { ECS_SERVER_GROUP_CONFIGURATION_SERVICE } from './serverGroupConfiguration.service';

module.exports = angular
  .module('spinnaker.ecs.serverGroupCommandBuilder.service', [
    INSTANCE_TYPE_SERVICE,
    ECS_SERVER_GROUP_CONFIGURATION_SERVICE,
  ])
  .factory('ecsServerGroupCommandBuilder', function($q, instanceTypeService, ecsServerGroupConfigurationService) {
    const CLOUD_PROVIDER = 'ecs';

    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};
      var credentialsLoader = AccountService.getCredentialsKeyedByAccount('ecs');

      var defaultCredentials = defaults.account || application.defaultCredentials.ecs;
      var defaultRegion = defaults.region || application.defaultRegions.ecs;

      var preferredZonesLoader = AccountService.getAvailabilityZonesForAccountAndRegion(
        'ecs',
        defaultCredentials,
        defaultRegion,
      );

      return $q
        .all({
          preferredZones: preferredZonesLoader,
          credentialsKeyedByAccount: credentialsLoader,
        })
        .then(function(asyncData) {
          var availabilityZones = asyncData.preferredZones;

          var defaultIamRole = 'None (No IAM role)';
          defaultIamRole = defaultIamRole.replace('{{application}}', application.name);

          var command = {
            application: application.name,
            credentials: defaultCredentials,
            region: defaultRegion,
            strategy: '',
            capacity: {
              min: 1,
              max: 1,
              desired: 1,
            },
            launchType: 'EC2',
            healthCheckType: 'EC2',
            selectedProvider: 'ecs',
            iamRole: defaultIamRole,
            availabilityZones: availabilityZones,
            autoscalingPolicies: [],
            subnetType: '',
            securityGroups: [],
            healthgraceperiod: '',
            placementStrategyName: '',
            placementStrategySequence: [],
            ecsClusterName: '',
            targetGroup: '',
            viewState: {
              useAllImageSelection: false,
              useSimpleCapacity: true,
              usePreferredZones: true,
              mode: defaults.mode || 'create',
              disableStrategySelection: true,
              dirty: {},
            },
          };

          if (
            application.attributes &&
            application.attributes.platformHealthOnlyShowOverride &&
            application.attributes.platformHealthOnly
          ) {
            command.interestingHealthProviderNames = ['ecs'];
          }

          return command;
        });
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {
      var pipelineCluster = _.cloneDeep(originalCluster);
      var region = Object.keys(pipelineCluster.availabilityZones)[0];
      // var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('ecs', pipelineCluster.instanceType);
      var commandOptions = { account: pipelineCluster.account, region: region };
      var asyncLoader = $q.all({ command: buildNewServerGroupCommand(application, commandOptions) });

      return asyncLoader.then(function(asyncData) {
        var command = asyncData.command;
        var zones = pipelineCluster.availabilityZones[region];
        var usePreferredZones = zones.join(',') === command.availabilityZones.join(',');

        var viewState = {
          instanceProfile: asyncData.instanceProfile,
          disableImageSelection: true,
          useSimpleCapacity:
            pipelineCluster.capacity.min === pipelineCluster.capacity.max && pipelineCluster.useSourceCapacity !== true,
          usePreferredZones: usePreferredZones,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
          templatingEnabled: true,
          existingPipelineCluster: true,
          dirty: {},
        };

        var viewOverrides = {
          region: region,
          credentials: pipelineCluster.account,
          availabilityZones: pipelineCluster.availabilityZones[region],
          viewState: viewState,
        };

        pipelineCluster.strategy = pipelineCluster.strategy || '';

        return angular.extend({}, command, pipelineCluster, viewOverrides);
      });
    }

    // Only used to prepare view requiring template selecting
    function buildNewServerGroupCommandForPipeline() {
      return $q.when({
        viewState: {
          requiresTemplateSelection: true,
        },
      });
    }

    function buildUpdateServerGroupCommand(serverGroup) {
      var command = {
        type: 'modifyAsg',
        asgs: [{ asgName: serverGroup.name, region: serverGroup.region }],
        healthCheckType: serverGroup.asg.healthCheckType,
        credentials: serverGroup.account,
      };
      ecsServerGroupConfigurationService.configureUpdateCommand(command);
      return command;
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode = 'clone') {
      var preferredZonesLoader = AccountService.getPreferredZonesByAccount('ecs');

      var serverGroupName = NameUtils.parseServerGroupName(serverGroup.asg.autoScalingGroupName);

      var asyncLoader = $q.all({
        preferredZones: preferredZonesLoader,
      });

      return asyncLoader.then(function(asyncData) {
        var zones = serverGroup.asg.availabilityZones.sort();
        var usePreferredZones = false;
        var preferredZonesForAccount = asyncData.preferredZones[serverGroup.account];
        if (preferredZonesForAccount) {
          var preferredZones = preferredZonesForAccount[serverGroup.region].sort();
          usePreferredZones = zones.join(',') === preferredZones.join(',');
        }

        // These processes should never be copied over, as the affect launching instances and enabling traffic
        let enabledProcesses = ['Launch', 'Terminate', 'AddToLoadBalancer'];

        var command = {
          application: application.name,
          strategy: '',
          stack: serverGroupName.stack,
          freeFormDetails: serverGroupName.freeFormDetails,
          credentials: serverGroup.account,
          healthCheckType: serverGroup.asg.healthCheckType,
          loadBalancers: serverGroup.asg.loadBalancerNames,
          region: serverGroup.region,
          useSourceCapacity: false,
          capacity: {
            min: serverGroup.asg.minSize,
            max: serverGroup.asg.maxSize,
            desired: serverGroup.asg.desiredCapacity,
          },
          availabilityZones: zones,
          selectedProvider: CLOUD_PROVIDER,
          source: {
            account: serverGroup.account,
            region: serverGroup.region,
            asgName: serverGroup.asg.autoScalingGroupName,
          },
          suspendedProcesses: (serverGroup.asg.suspendedProcesses || [])
            .map(process => process.processName)
            .filter(name => !enabledProcesses.includes(name)),
          targetGroup: serverGroup.targetGroup,
          viewState: {
            instanceProfile: asyncData.instanceProfile,
            useAllImageSelection: false,
            useSimpleCapacity: serverGroup.asg.minSize === serverGroup.asg.maxSize,
            usePreferredZones: usePreferredZones,
            mode: mode,
            isNew: false,
            dirty: {},
          },
        };

        if (mode === 'clone' || mode === 'editPipeline') {
          command.useSourceCapacity = true;
        }

        if (mode === 'editPipeline') {
          command.strategy = 'redblack';
          command.suspendedProcesses = [];
        }

        if (serverGroup.launchConfig) {
          angular.extend(command, {
            iamRole: serverGroup.launchConfig.iamInstanceProfile,
          });
          if (serverGroup.launchConfig.userData) {
            command.base64UserData = serverGroup.launchConfig.userData;
          }
          command.viewState.imageId = serverGroup.launchConfig.imageId;
        }

        return command;
      });
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
      buildUpdateServerGroupCommand: buildUpdateServerGroupCommand,
    };
  });
