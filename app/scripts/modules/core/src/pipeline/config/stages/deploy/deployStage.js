'use strict';

import { CLUSTER_SERVICE } from 'core/cluster/cluster.service';
import { CloudProviderRegistry } from 'core/cloudProvider';
import { NameUtils } from 'core/naming';
import { SERVER_GROUP_COMMAND_BUILDER_SERVICE } from 'core/serverGroup/configure/common/serverGroupCommandBuilder.service';
import { StageConstants } from 'core/pipeline/config/stages/stageConstants';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.deployStage', [SERVER_GROUP_COMMAND_BUILDER_SERVICE, CLUSTER_SERVICE])
  .config(function(pipelineConfigProvider, clusterServiceProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Deploy',
      description: 'Deploys the previously baked or found image',
      strategyDescription: 'Deploys the image specified',
      key: 'deploy',
      alias: 'createServerGroup',
      templateUrl: require('./deployStage.html'),
      executionDetailsUrl: require('./deployExecutionDetails.html'),
      controller: 'DeployStageCtrl',
      controllerAs: 'deployStageCtrl',
      defaultTimeoutMs: 2 * 60 * 60 * 1000, // 2 hours
      validators: [
        {
          type: 'stageBeforeType',
          stageTypes: ['bake', 'findAmi', 'findImage', 'findImageFromTags'],
          message: 'You must have a Bake or Find Image stage before any deploy stage.',
          skipValidation: (pipeline, stage) =>
            (stage.clusters || []).every(
              cluster =>
                CloudProviderRegistry.getValue(cluster.provider, 'serverGroup.skipUpstreamStageCheck') ||
                clusterServiceProvider.$get().isDeployingArtifact(cluster),
            ),
        },
      ],
      accountExtractor: stage => (stage.context.clusters || []).map(c => c.account),
      configAccountExtractor: stage => (stage.clusters || []).map(c => c.account),
      strategy: true,
    });
  })
  .controller('DeployStageCtrl', function(
    $injector,
    $scope,
    $uibModal,
    stage,
    providerSelectionService,
    serverGroupCommandBuilder,
    serverGroupTransformer,
  ) {
    $scope.stage = stage;

    function initializeCommand() {
      $scope.stage.clusters = $scope.stage.clusters || [];
    }

    this.getRegion = function(cluster) {
      if (cluster.region) {
        return cluster.region;
      }
      var availabilityZones = cluster.availabilityZones;
      if (availabilityZones) {
        var regions = Object.keys(availabilityZones);
        if (regions && regions.length) {
          return regions[0];
        }
      }
      return 'n/a';
    };

    this.hasSubnetDeployments = () => {
      return stage.clusters.some(cluster => {
        let cloudProvider = cluster.cloudProvider || cluster.provider || cluster.providerType || 'aws';
        return CloudProviderRegistry.hasValue(cloudProvider, 'subnet');
      });
    };

    this.hasInstanceTypeDeployments = () => {
      return stage.clusters.some(cluster => {
        return cluster.instanceType !== undefined;
      });
    };

    this.getSubnet = cluster => {
      let cloudProvider = cluster.cloudProvider || cluster.provider || cluster.providerType || 'aws';
      if (CloudProviderRegistry.hasValue(cloudProvider, 'subnet')) {
        let subnetRenderer = CloudProviderRegistry.getValue(cloudProvider, 'subnet').renderer;
        if ($injector.has(subnetRenderer)) {
          return $injector.get(subnetRenderer).render(cluster);
        } else {
          throw new Error('No "' + subnetRenderer + '" service found for provider "' + cloudProvider + '".');
        }
      } else {
        return '[none]';
      }
    };

    this.getClusterName = function(cluster) {
      return NameUtils.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
    };

    this.addCluster = function() {
      providerSelectionService
        .selectProvider($scope.application, 'serverGroup', providerFilterFn)
        .then(function(selectedProvider) {
          let config = CloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
          $uibModal
            .open({
              templateUrl: config.cloneServerGroupTemplateUrl,
              controller: `${config.cloneServerGroupController} as ctrl`,
              size: 'lg',
              resolve: {
                title: function() {
                  return 'Configure Deployment Cluster';
                },
                application: function() {
                  return $scope.application;
                },
                serverGroupCommand: function() {
                  return serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(
                    selectedProvider,
                    $scope.stage,
                    $scope.$parent.pipeline,
                  );
                },
              },
            })
            .result.then(function(command) {
              // If we don't set the provider, the serverGroupTransformer won't know which provider to delegate to.
              command.provider = selectedProvider;
              var stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
              delete stageCluster.credentials;
              $scope.stage.clusters.push(stageCluster);
            })
            .catch(() => {});
        });
    };

    this.editCluster = function(cluster, index) {
      cluster.provider = cluster.cloudProvider || cluster.providerType || 'aws';
      let providerConfig = CloudProviderRegistry.getProvider(cluster.provider);
      return $uibModal
        .open({
          templateUrl: providerConfig.serverGroup.cloneServerGroupTemplateUrl,
          controller: `${providerConfig.serverGroup.cloneServerGroupController} as ctrl`,
          size: 'lg',
          resolve: {
            title: function() {
              return 'Configure Deployment Cluster';
            },
            application: function() {
              return $scope.application;
            },
            serverGroupCommand: function() {
              return serverGroupCommandBuilder.buildServerGroupCommandFromPipeline(
                $scope.application,
                cluster,
                $scope.stage,
                $scope.$parent.pipeline,
              );
            },
          },
        })
        .result.then(function(command) {
          var stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
          delete stageCluster.credentials;
          $scope.stage.clusters[index] = stageCluster;
        })
        .catch(() => {});
    };

    this.copyCluster = function(index) {
      $scope.stage.clusters.push(angular.copy($scope.stage.clusters[index]));
    };

    this.removeCluster = function(index) {
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
        $helperChildren.each(index => {
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
  });
