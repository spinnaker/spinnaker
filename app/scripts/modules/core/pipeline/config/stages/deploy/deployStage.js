'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deployStage', [
  require('../../../../serverGroup/serverGroup.read.service.js'),
  require('../../../../serverGroup/configure/common/serverGroupCommandBuilder.js'),
  require('../../../../cloudProvider/cloudProvider.registry.js'),
  require('../stageConstants.js'),
])
  .config(function (pipelineConfigProvider) {
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
          stageTypes: ['bake', 'findAmi', 'findImage'],
          message: 'You must have a Bake or Find Image stage before any deploy stage.'
        },
      ],
      strategy: true,
    });
  })
  .controller('DeployStageCtrl', function ($scope, $uibModal, stage, namingService, providerSelectionService,
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

    this.hasAmazonDeployments = () => {
      return stage.clusters.some((cluster) => cluster.provider === 'aws');
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
          resolve: {
            title: function () {
              return 'Configure Deployment Cluster';
            },
            application: function () {
              return $scope.application;
            },
            serverGroupCommand: function () {
              return serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(selectedProvider);
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
        resolve: {
          title: function () {
            return 'Configure Deployment Cluster';
          },
          application: function () {
            return $scope.application;
          },
          serverGroupCommand: function () {
            return serverGroupCommandBuilder.buildServerGroupCommandFromPipeline($scope.application, cluster);
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
