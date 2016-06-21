'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.runJobStage', [
  require('../../../../job/job.read.service.js'),
  require('../../../../job/configure/common/jobCommandBuilder.js'),
  require('../../../../cloudProvider/cloudProvider.registry.js'),
])
  .config(function (pipelineConfigProvider, cloudProviderRegistryProvider, settings) {
    if (!settings.feature.jobs) {
      return;
    }

    pipelineConfigProvider.registerStage({
      label: 'Run Job',
      description: 'Runs the previously baked or found image',
      strategyDescription: 'Runs the image specified',
      key: 'runJobs',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      controller: 'RunJobStageCtrl',
      controllerAs: 'runJobStageCtrl',
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        {
          type: 'stageBeforeType',
          stageTypes: ['bake', 'findAmi', 'findImage'],
          message: 'You must have a Bake or Find Image stage before any runJob stage.',
          skipValidation: (pipeline, stage) => {
            if (!stage.clusters || !stage.clusters.length) {
              return true;
            }
            return stage.clusters.every(cluster =>
              cloudProviderRegistryProvider.$get().getValue(cluster.provider, 'job.skipUpstreamStageCheck')
            );
          }
        },
      ],
      strategy: true,
    });
  })
  .controller('RunJobStageCtrl', function ($scope, $uibModal, stage, namingService, providerSelectionService,
                                           cloudProviderRegistry, jobCommandBuilder, jobTransformer) {
    $scope.stage = stage;

    function initializeCommand() {
      $scope.stage.clusters = $scope.stage.clusters || [];
    }

    this.getRegion = function(cluster) {
      if (cluster.region) {
        return cluster.region;
      }
      return 'n/a';
    };

    this.getClusterName = function(cluster) {
      return namingService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
    };

    this.addCluster = function() {
      providerSelectionService.selectProvider($scope.application, 'job').then(function(selectedProvider) {
        let config = cloudProviderRegistry.getValue(selectedProvider, 'job');
        $uibModal.open({
          templateUrl: config.cloneJobTemplateUrl,
          controller: `${config.cloneJobController} as ctrl`,
          size: 'lg',
          resolve: {
            title: function () {
              return 'Configure Job Cluster';
            },
            application: function () {
              return $scope.application;
            },
            jobCommand: function () {
              return jobCommandBuilder.buildNewJobCommandForPipeline(selectedProvider, $scope.stage, $scope.$parent.pipeline);
            },
          }
        }).result.then(function(command) {
            // If we don't set the provider, the jobTransformer won't know which provider to delegate to.
            command.provider = selectedProvider;
            var stageCluster = jobTransformer.convertJobCommandToRunConfiguration(command);
            delete stageCluster.credentials;
            $scope.stage.clusters.push(stageCluster);
          });
      });
    };

    this.editCluster = function(cluster, index) {
      cluster.provider = cluster.cloudProvider || cluster.providerType || 'kubernetes';
      $scope.stage.cloudProvider = cluster.provider;
      let providerConfig = cloudProviderRegistry.getProvider(cluster.provider);
      return $uibModal.open({
        templateUrl: providerConfig.job.cloneJobTemplateUrl,
        controller: `${providerConfig.job.cloneJobController} as ctrl`,
        size: 'lg',
        resolve: {
          title: function () {
            return 'Configure Job Cluster';
          },
          application: function () {
            return $scope.application;
          },
          jobCommand: function () {
            return jobCommandBuilder.buildJobCommandFromPipeline($scope.application, cluster, $scope.stage, $scope.$parent.pipeline);
          },
        }
      }).result.then(function(command) {
          var stageCluster = jobTransformer.convertJobCommandToRunConfiguration(command);
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
  });
