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
  .factory('ecsServerGroupCommandBuilder', [
    '$q',
    'instanceTypeService',
    'ecsServerGroupConfigurationService',
    function($q, instanceTypeService, ecsServerGroupConfigurationService) {
      const CLOUD_PROVIDER = 'ecs';

      function reconcileUpstreamImages(image, upstreamImages) {
        if (image.fromContext) {
          let matchingImage = upstreamImages.find(otherImage => image.stageId === otherImage.stageId);

          if (matchingImage) {
            image.cluster = matchingImage.cluster;
            image.pattern = matchingImage.pattern;
            image.repository = matchingImage.repository;
            return image;
          } else {
            return null;
          }
        } else if (image.fromTrigger) {
          let matchingImage = upstreamImages.find(otherImage => {
            return (
              image.registry === otherImage.registry &&
              image.repository === otherImage.repository &&
              image.tag === otherImage.tag
            );
          });

          if (matchingImage) {
            return image;
          } else {
            return null;
          }
        } else {
          return image;
        }
      }

      function findUpstreamImages(current, all, visited = {}) {
        // This actually indicates a loop in the stage dependencies.
        if (visited[current.refId]) {
          return [];
        } else {
          visited[current.refId] = true;
        }
        let result = [];
        if (current.type === 'findImageFromTags') {
          result.push({
            fromContext: true,
            imageLabelOrSha: current.imageLabelOrSha,
            stageId: current.refId,
          });
        }
        current.requisiteStageRefIds.forEach(function(id) {
          let next = all.find(stage => stage.refId === id);
          if (next) {
            result = result.concat(findUpstreamImages(next, all, visited));
          }
        });

        return result;
      }

      function findTriggerImages(triggers) {
        let result = triggers
          .filter(trigger => {
            return trigger.type === 'docker';
          })
          .map(trigger => {
            return {
              fromTrigger: true,
              repository: trigger.repository,
              account: trigger.account,
              organization: trigger.organization,
              registry: trigger.registry,
              tag: trigger.tag,
            };
          });

        return result;
      }

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

            var defaultImageCredentials = 'None (No registry credentials)';

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
              dockerImageCredentialsSecret: defaultImageCredentials,
              availabilityZones: availabilityZones,
              subnetType: '',
              securityGroups: [],
              healthCheckGracePeriodSeconds: '',
              placementConstraints: [],
              placementStrategyName: '',
              placementStrategySequence: [],
              ecsClusterName: '',
              targetGroup: '',
              copySourceScalingPoliciesAndActions: true,
              useSourceCapacity: true,
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

      function buildServerGroupCommandFromPipeline(application, originalCluster, current, pipeline) {
        var pipelineCluster = _.cloneDeep(originalCluster);
        var region = Object.keys(pipelineCluster.availabilityZones)[0];
        // var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('ecs', pipelineCluster.instanceType);
        var commandOptions = { account: pipelineCluster.account, region: region };
        var asyncLoader = $q.all({ command: buildNewServerGroupCommand(application, commandOptions) });

        return asyncLoader.then(function(asyncData) {
          var command = asyncData.command;
          var zones = pipelineCluster.availabilityZones[region];
          var usePreferredZones = zones.join(',') === command.availabilityZones.join(',');

          let contextImages = findUpstreamImages(current, pipeline.stages) || [];
          contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));

          if (command.docker && command.docker.image) {
            command.docker.image = reconcileUpstreamImages(command.docker.image, contextImages);
          }

          var viewState = {
            instanceProfile: asyncData.instanceProfile,
            disableImageSelection: true,
            useSimpleCapacity:
              pipelineCluster.capacity.min === pipelineCluster.capacity.max &&
              pipelineCluster.useSourceCapacity !== true,
            usePreferredZones: usePreferredZones,
            mode: 'editPipeline',
            submitButtonLabel: 'Done',
            templatingEnabled: true,
            existingPipelineCluster: true,
            dirty: {},
            contextImages: contextImages,
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
      function buildNewServerGroupCommandForPipeline(current, pipeline) {
        let contextImages = findUpstreamImages(current, pipeline.stages) || [];
        contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));

        return $q.when({
          viewState: {
            contextImages: contextImages,
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
            useSourceCapacity: true,
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
            copySourceScalingPoliciesAndActions: true,
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
    },
  ]);
