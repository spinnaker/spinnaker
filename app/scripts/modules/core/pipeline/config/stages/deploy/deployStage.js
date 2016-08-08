'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deployStage', [
  require('../../../../serverGroup/serverGroup.read.service.js'),
  require('../../../../serverGroup/configure/common/serverGroupCommandBuilder.js'),
  require('../../../../cloudProvider/cloudProvider.registry.js'),
  require('../stageConstants.js'),
])
  .config(function (pipelineConfigProvider, cloudProviderRegistryProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Deploy',
      description: 'Deploys the previously baked or found image',
      strategyDescription: 'Deploys the image specified',
      key: 'deploy',
      templateUrl: require('./deployStage.html'),
      executionDetailsUrl: require('./deployExecutionDetails.html'),
      controller: 'DeployStageCtrl',
      controllerAs: 'deployStageCtrl',
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        {
          type: 'stageBeforeType',
          stageTypes: ['bake', 'findAmi', 'findImage', 'findImageFromTags'],
          message: 'You must have a Bake or Find Image stage before any deploy stage.',
          skipValidation: (pipeline, stage) => {
            if (!stage.clusters || !stage.clusters.length) {
              return true;
            }
            return stage.clusters.every(cluster =>
              cloudProviderRegistryProvider.$get().getValue(cluster.provider, 'serverGroup.skipUpstreamStageCheck')
            );
          }
        },
      ],
      strategy: true,
    });
  })
  .controller('DeployStageCtrl', function ($injector, $scope, $uibModal, stage, namingService, providerSelectionService,
                                           cloudProviderRegistry, serverGroupCommandBuilder, serverGroupTransformer, stageConstants) {
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
      return stage.clusters.some((cluster) => {
        let cloudProvider = cluster.cloudProvider || cluster.provider || cluster.providerType || 'aws';
        return cloudProviderRegistry.hasValue(cloudProvider, 'subnet');
      });
    };

    this.hasInstanceTypeDeployments = () => {
      return stage.clusters.some((cluster) => {
        return cluster.instanceType !== undefined;
      });
    };

    this.getSubnet = (cluster) => {
      let cloudProvider = cluster.cloudProvider || cluster.provider || cluster.providerType || 'aws';
      if (cloudProviderRegistry.hasValue(cloudProvider, 'subnet')) {
        let subnetRenderer = cloudProviderRegistry.getValue(cloudProvider, 'subnet').renderer;
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
      return namingService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
    };

    this.addCluster = function() {
      providerSelectionService.selectProvider($scope.application).then(function(selectedProvider) {
        let config = cloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
        $uibModal.open({
          templateUrl: config.cloneServerGroupTemplateUrl,
          controller: `${config.cloneServerGroupController} as ctrl`,
          size: 'lg',
          resolve: {
            title: function () {
              return 'Configure Deployment Cluster';
            },
            application: function () {
              return $scope.application;
            },
            serverGroupCommand: function () {
              return serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(selectedProvider, $scope.stage, $scope.$parent.pipeline);
            },
          }
        }).result.then(function(command) {
            // If we don't set the provider, the serverGroupTransformer won't know which provider to delegate to.
            command.provider = selectedProvider;
            var stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
            delete stageCluster.credentials;
            $scope.stage.clusters.push(stageCluster);
          });
      });
    };

    this.editCluster = function(cluster, index) {
      cluster.provider = cluster.cloudProvider || cluster.providerType || 'aws';
      let providerConfig = cloudProviderRegistry.getProvider(cluster.provider);
      return $uibModal.open({
        templateUrl: providerConfig.serverGroup.cloneServerGroupTemplateUrl,
        controller: `${providerConfig.serverGroup.cloneServerGroupController} as ctrl`,
        size: 'lg',
        resolve: {
          title: function () {
            return 'Configure Deployment Cluster';
          },
          application: function () {
            return $scope.application;
          },
          serverGroupCommand: function () {
            return serverGroupCommandBuilder.buildServerGroupCommandFromPipeline($scope.application, cluster, $scope.stage, $scope.$parent.pipeline);
          },
        }
      }).result.then(function(command) {
          var stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
          delete stageCluster.credentials;
          $scope.stage.clusters[index] = stageCluster;
        });
    };

    this.copyCluster = function(index) {
      $scope.stage.clusters.push(angular.copy($scope.stage.clusters[index]));
    };

    this.removeCluster = function(index) {
      $scope.stage.clusters.splice(index, 1);
    };

    initializeCommand();

    $scope.trafficOptions = stageConstants.strategyTrafficOptions;

    if ($scope.pipeline.strategy) {
      $scope.stage.trafficOptions = $scope.stage.trafficOptions || stageConstants.strategyTrafficOptions[0].val;
    }

  });
