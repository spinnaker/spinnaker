'use strict';

import * as angular from 'angular';
import { CloudProviderRegistry, ProviderSelectionService } from '../../../../cloudProvider';
import { CLUSTER_SERVICE } from '../../../../cluster/cluster.service';
import { NameUtils } from '../../../../naming';
import { Registry } from '../../../../registry';
import { SERVER_GROUP_COMMAND_BUILDER_SERVICE } from '../../../../serverGroup/configure/common/serverGroupCommandBuilder.service';

import { StageConstants } from '../stageConstants';

export const CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE = 'spinnaker.core.pipeline.stage.deployStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE, [SERVER_GROUP_COMMAND_BUILDER_SERVICE, CLUSTER_SERVICE])
  .config([
    'clusterServiceProvider',
    function (clusterServiceProvider) {
      Registry.pipeline.registerStage({
        label: 'Deploy',
        description: 'Deploys the previously baked or found image',
        strategyDescription: 'Deploys the image specified',
        key: 'deploy',
        alias: 'createServerGroup',
        templateUrl: require('./deployStage.html'),
        executionDetailsUrl: require('./deployExecutionDetails.html'),
        controller: 'DeployStageCtrl',
        controllerAs: 'deployStageCtrl',
        supportsCustomTimeout: true,
        validators: [
          {
            type: 'stageBeforeType',
            stageTypes: ['bake', 'findAmi', 'findImage', 'findImageFromTags'],
            message: 'You must have a Bake or Find Image stage before any deploy stage.',
            skipValidation: (pipeline, stage) =>
              (stage.clusters || []).every(
                (cluster) =>
                  CloudProviderRegistry.getValue(cluster.provider, 'serverGroup.skipUpstreamStageCheck') ||
                  clusterServiceProvider.$get().isDeployingArtifact(cluster),
              ),
          },
        ],
        accountExtractor: (stage) => (stage.context.clusters || []).map((c) => c.account),
        configAccountExtractor: (stage) => (stage.clusters || []).map((c) => c.account),
        artifactExtractor: (stageContext) => {
          const clusterService = clusterServiceProvider.$get();
          // We'll either be in the context of the entire stage, and have an array of clusters,
          // or will be in the context of a single cluster, in which case relevant fields will be
          // directly on stageContext
          const clusters = stageContext.clusters || [stageContext];
          return clusters
            .map(clusterService.extractArtifacts, clusterService)
            .reduce((array, items) => array.concat(items), []);
        },
        artifactRemover: (stage, artifactId) => {
          const clusterService = clusterServiceProvider.$get();
          (stage.clusters || []).forEach((cluster) =>
            clusterService.getArtifactExtractor(cluster.cloudProvider).removeArtifact(cluster, artifactId),
          );
        },
        strategy: true,
      });
    },
  ])
  .controller('DeployStageCtrl', [
    '$injector',
    '$scope',
    '$uibModal',
    'stage',
    'serverGroupCommandBuilder',
    'serverGroupTransformer',
    function ($injector, $scope, $uibModal, stage, serverGroupCommandBuilder, serverGroupTransformer) {
      $scope.stage = stage;

      function initializeCommand() {
        $scope.stage.clusters = $scope.stage.clusters || [];
      }

      this.getRegion = function (cluster) {
        if (cluster.region) {
          return cluster.region;
        }
        const availabilityZones = cluster.availabilityZones;
        if (availabilityZones) {
          const regions = Object.keys(availabilityZones);
          if (regions && regions.length) {
            return regions[0];
          }
        }
        return 'n/a';
      };

      this.hasSubnetDeployments = () => {
        return stage.clusters.some((cluster) => {
          const cloudProvider = cluster.cloudProvider || cluster.provider || cluster.providerType || 'aws';
          return CloudProviderRegistry.hasValue(cloudProvider, 'subnet');
        });
      };

      this.hasInstanceTypeDeployments = () => {
        return stage.clusters.some((cluster) => {
          return cluster.instanceType !== undefined;
        });
      };

      this.getSubnet = (cluster) => {
        const cloudProvider = cluster.cloudProvider || cluster.provider || cluster.providerType || 'aws';
        if (CloudProviderRegistry.hasValue(cloudProvider, 'subnet')) {
          const subnetRenderer = CloudProviderRegistry.getValue(cloudProvider, 'subnet').renderer;
          if ($injector.has(subnetRenderer)) {
            return $injector.get(subnetRenderer).render(cluster);
          } else {
            throw new Error('No "' + subnetRenderer + '" service found for provider "' + cloudProvider + '".');
          }
        } else {
          return '[none]';
        }
      };

      this.getClusterName = function (cluster) {
        return NameUtils.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
      };

      this.addCluster = function () {
        ProviderSelectionService.selectProvider($scope.application, 'serverGroup', providerFilterFn).then(function (
          selectedProvider,
        ) {
          const config = CloudProviderRegistry.getValue(selectedProvider, 'serverGroup');

          const handleResult = function (command) {
            // If we don't set the provider, the serverGroupTransformer won't know which provider to delegate to.
            command.provider = selectedProvider;
            const stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
            delete stageCluster.credentials;
            $scope.stage.clusters.push(stageCluster);
          };

          const title = 'Configure Deployment Cluster';
          const application = $scope.application;
          serverGroupCommandBuilder
            .buildNewServerGroupCommandForPipeline(selectedProvider, $scope.stage, $scope.$parent.pipeline)
            .then((command) => {
              if (config.CloneServerGroupModal) {
                // react
                return config.CloneServerGroupModal.show({ title, application, command });
              } else {
                // angular
                return $uibModal.open({
                  templateUrl: config.cloneServerGroupTemplateUrl,
                  controller: `${config.cloneServerGroupController} as ctrl`,
                  size: 'lg',
                  windowClass: 'modal-z-index',
                  resolve: {
                    title: () => title,
                    application: () => application,
                    serverGroupCommand: () => command,
                  },
                }).result;
              }
            })
            .then(handleResult)
            .catch(() => {});
        });
      };

      this.editCluster = function (cluster, index) {
        cluster.provider = cluster.cloudProvider || cluster.providerType || 'aws';
        const providerConfig = CloudProviderRegistry.getProvider(cluster.provider);

        const handleResult = function (command) {
          const stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
          delete stageCluster.credentials;
          $scope.stage.clusters[index] = stageCluster;
        };

        const title = 'Configure Deployment Cluster';
        const application = $scope.application;
        serverGroupCommandBuilder
          .buildServerGroupCommandFromPipeline(application, cluster, $scope.stage, $scope.$parent.pipeline)
          .then((command) => {
            if (providerConfig.serverGroup.CloneServerGroupModal) {
              // react
              return providerConfig.serverGroup.CloneServerGroupModal.show({ title, application, command });
            } else {
              // angular
              return $uibModal.open({
                templateUrl: providerConfig.serverGroup.cloneServerGroupTemplateUrl,
                controller: `${providerConfig.serverGroup.cloneServerGroupController} as ctrl`,
                size: 'lg',
                windowClass: 'modal-z-index',
                resolve: {
                  title: () => title,
                  application: () => application,
                  serverGroupCommand: () => command,
                },
              }).result;
            }
          })
          .then(handleResult)
          .catch(() => {});
      };

      this.copyCluster = function (index) {
        $scope.stage.clusters.push(angular.copy($scope.stage.clusters[index]));
      };

      this.removeCluster = function (index) {
        $scope.stage.clusters.splice(index, 1);
      };

      this.clusterSortOptions = {
        axis: 'y',
        delay: 150,
        start: (_event, ui) => {
          // Calculate placeholder height accurately
          ui.placeholder.height(ui.item.height());
        },
        helper: (_event, element) => {
          // Calcluate helper cell widths accurately
          const $originalChildren = element.children();
          const $helper = element.clone();
          const $helperChildren = $helper.children();
          $helperChildren.each((index) => {
            $helperChildren.eq(index).width($originalChildren[index].clientWidth);
          });
          return $helper;
        },
        handle: '.handle',
      };

      initializeCommand();

      $scope.trafficOptions = StageConstants.STRATEGY_TRAFFIC_OPTIONS;

      if ($scope.pipeline.strategy) {
        $scope.stage.trafficOptions = $scope.stage.trafficOptions || $scope.trafficOptions[0].val;
      }

      function providerFilterFn(application, account, provider) {
        return !provider.unsupportedStageTypes || provider.unsupportedStageTypes.indexOf('deploy') === -1;
      }
    },
  ]);
