'use strict';

angular.module('deckApp.pipelines.stage.canary')
  .config(function (pipelineConfigProvider, settings) {
    if (settings.feature.canary === true) {
        pipelineConfigProvider.registerStage({
          label: 'Canary',
          description: 'Canary tests new changes against a baseline version',
          key: 'canary',
          templateUrl: 'scripts/modules/pipelines/config/stages/canary/canaryStage.html',
          executionDetailsUrl: 'scripts/modules/pipelines/config/stages/canary/canaryExecutionDetails.html',
          executionSummaryUrl: 'scripts/modules/pipelines/config/stages/canary/canaryExecutionSummary.html',
          executionLabelTemplateUrl: 'scripts/modules/pipelines/config/stages/canary/canaryExecutionLabel.html',
          controller: 'CanaryStageCtrl',
          controllerAs: 'canaryStageCtrl',
          validators: [
            {
              type: 'stageBeforeType',
              stageTypes: ['bake', 'findAmi'],
              message: 'You must have a Bake or Find AMI stage before a canary stage.'
            },
          ],
        });
    }
  })
  .controller('CanaryStageCtrl', function ($scope, $modal, stage, namingService, providerSelectionService, serverGroupCommandBuilder, awsServerGroupTransformer) {
    $scope.stage = stage;
    $scope.stage.scaleUp = $scope.stage.scaleUp || {};
    $scope.stage.owner = $scope.stage.owner || {};
    $scope.stage.watchers = $scope.stage.watchers || [];
    $scope.stage.canaries = $scope.stage.canaries || [];
    $scope.stage.canaryConfig = $scope.stage.canaryConfig || {};
    $scope.stage.canaryConfig.canaryAnalysisConfig = $scope.stage.canaryConfig.canaryAnalysisConfig || {};
    $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours = null; //$scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours || [];
    /*
    console.log($scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours);
    console.log(typeof $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours);
    $scope.notificationHours = $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours.join(',');
    console.log($scope.notificationHours);
    console.log(typeof $scope.notificationHours);

    this.splitNotificationHours = function() {
      console.log("ngchanged");
      $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours = _.map($scope.notificationHours.split(','), function(str) {
        return str.trim();
      });
      console.log($scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours)
      console.log(typeof $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours);
    };
*/
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
              return 'Configure Canary Cluster';
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
            delete stageCluster.credentials;
            $scope.stage.canaries.push(stageCluster);
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
          delete stageCluster.credentials;
          $scope.stage.canaries[index] = stageCluster;
        });
    };

    this.copyCluster = function(index) {
      $scope.stage.canaries.push(angular.copy($scope.stage.canaries[index]));
    };

    this.removeCluster = function(index) {
      $scope.stage.canaries.splice(index, 1);
    };
  });
