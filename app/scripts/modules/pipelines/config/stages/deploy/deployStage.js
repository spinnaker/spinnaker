'use strict';

angular.module('deckApp.pipelines.stage.deploy')
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Deploy',
      description: 'Deploys the previously baked AMI',
      key: 'deploy',
      templateUrl: 'scripts/modules/pipelines/config/stages/deploy/deployStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/deploy/deployExecutionDetails.html',
      controller: 'DeployStageCtrl',
      controllerAs: 'deployStageCtrl',
      validators: [
        {
          type: 'stageBeforeType',
          stageType: 'bake',
          message: 'You must have a bake stage immediately before any deploy stage.'
        },
      ],
    });
  })
  .controller('DeployStageCtrl', function ($scope, $modal, stage, namingService, providerSelectionService, serverGroupCommandBuilder, awsServerGroupTransformer) {
    $scope.stage = stage;

    function initializeCommand() {
      // TODO: We can probably remove this once we've migrated everyone over to multi-cluster deploy stages.
      // This is the lazy way to get us there without explicitly editing all the existing pipelines
      $scope.stage.clusters = $scope.stage.clusters || [];
      if ($scope.stage.cluster) {
        $scope.stage.cluster.account = $scope.stage.account;
        $scope.stage.clusters = [$scope.stage.cluster];

        delete $scope.stage.cluster;
        delete $scope.stage.account;
      }
    }

    this.getRegion = function(cluster) {
      var availabilityZones = cluster.availabilityZones;
      if (availabilityZones) {
        var regions = Object.keys(availabilityZones);
        if (regions && regions.length) {
          return regions[0];
        }
      }
      return 'n/a';
    };

    this.getClusterName = function(cluster) {
      return namingService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
    };

    this.addCluster = function() {
      providerSelectionService.selectProvider().then(function(selectedProvider) {
        $modal.open({
          templateUrl: 'scripts/modules/serverGroups/configure/' + selectedProvider + '/wizard/serverGroupWizard.html',
          controller: selectedProvider + 'CloneServerGroupCtrl as ctrl',
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
            var stageCluster = awsServerGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
            $scope.stage.clusters.push(stageCluster);
          });
      });
    };

    this.editCluster = function(cluster, index) {
      cluster.provider = cluster.provider || 'aws';
      return $modal.open({
        templateUrl: 'scripts/modules/serverGroups/configure/' + cluster.provider + '/wizard/serverGroupWizard.html',
        controller: cluster.provider + 'CloneServerGroupCtrl as ctrl',
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
          var stageCluster = awsServerGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
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
