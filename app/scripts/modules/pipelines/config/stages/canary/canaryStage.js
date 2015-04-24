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
  .controller('CanaryStageCtrl', function ($scope, $modal, stage, namingService, providerSelectionService, serverGroupCommandBuilder, awsServerGroupTransformer, accountService) {
    $scope.stage = stage;
    $scope.stage.scaleUp = $scope.stage.scaleUp || {};
    $scope.stage.owner = $scope.stage.owner || {};
    $scope.stage.watchers = $scope.stage.watchers || [];
    $scope.stage.canaries = $scope.stage.canaries || [];
    $scope.stage.canaryConfig = $scope.stage.canaryConfig || {};
    $scope.stage.canaryConfig.canaryAnalysisConfig = $scope.stage.canaryConfig.canaryAnalysisConfig || {};
    $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours = $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours || [];

    accountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
    });

    $scope.notificationHours = $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours.join(',');

    this.splitNotificationHours = function() {
      $scope.stage.canaryConfig.canaryAnalysisConfig.notificationHours = _.map($scope.notificationHours.split(','), function(str) {
        if (!parseInt(str.trim()).isNaN) {
          return parseInt(str.trim());
        }
        return 0;
      });
    };

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

    function getClusterName(cluster) {
      return namingService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
    }

    this.getClusterName = getClusterName;

    function cleanupClusterConfig(cluster, type) {
      delete cluster.credentials;
      if (cluster.freeFormDetails && cluster.freeFormDetails.split('-').pop() === type.toLowerCase()) {
        return;
      }
      if (cluster.freeFormDetails) {
        cluster.freeFormDetails += '-';
      }
      cluster.freeFormDetails += type.toLowerCase();
      cluster.clusterName = getClusterName(cluster);
    }

    function configureServerGroupCommandForEditing(command) {
      command.viewState.disableStrategySelection = true;
      command.viewState.hideClusterNamePreview = true;
      command.viewState.readOnlyFields = { credentials: true, region: true, subnet: true };
    }

    this.addClusterPair = function() {
      $scope.stage.clusterPairs = $scope.stage.clusterPairs || [];
      providerSelectionService.selectProvider().then(function(selectedProvider) {
        $modal.open({
          templateUrl: 'scripts/modules/serverGroups/configure/' + selectedProvider + '/wizard/serverGroupWizard.html',
          controller: selectedProvider + 'CloneServerGroupCtrl as ctrl',
          resolve: {
            title: function () {
              return 'Add Cluster Pair';
            },
            application: function () {
              return $scope.application;
            },
            serverGroupCommand: function () {
              return serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(selectedProvider)
                .then(function(command) {
                  configureServerGroupCommandForEditing(command);
                  command.viewState.disableNoTemplateSelection = true;
                  command.viewState.customTemplateMessage = 'Select a template to configure the canary and baseline ' +
                    'cluster pair. If you want to configure the server groups differently, you can do so by clicking ' +
                    '"Edit" after adding the pair.';
                  return command;
                });
            },
          }
        }).result.then(function(command) {
            var baselineCluster = awsServerGroupTransformer.convertServerGroupCommandToDeployConfiguration(command),
                canaryCluster = _.cloneDeep(baselineCluster);
            cleanupClusterConfig(baselineCluster, 'baseline');
            cleanupClusterConfig(canaryCluster, 'canary');
            $scope.stage.clusterPairs.push({baseline: baselineCluster, canary: canaryCluster});
          });
      });
    };

    this.editCluster = function(cluster, index, type) {
      cluster.provider = cluster.provider || 'aws';
      return $modal.open({
        templateUrl: 'scripts/modules/serverGroups/configure/' + cluster.provider + '/wizard/serverGroupWizard.html',
        controller: cluster.provider + 'CloneServerGroupCtrl as ctrl',
        resolve: {
          title: function () {
            return 'Configure ' + type + ' Cluster';
          },
          application: function () {
            return $scope.application;
          },
          serverGroupCommand: function () {
            return serverGroupCommandBuilder.buildServerGroupCommandFromPipeline($scope.application, cluster)
              .then(function(command) {
                configureServerGroupCommandForEditing(command);
                var detailsParts = command.freeFormDetails.split('-');
                var lastPart = detailsParts.pop();
                if (lastPart === type.toLowerCase()) {
                  command.freeFormDetails = detailsParts.join('-');
                }
                return command;
              });
          },
        }
      }).result.then(function(command) {
          var stageCluster = awsServerGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
          cleanupClusterConfig(stageCluster, type);
          $scope.stage.clusterPairs[index][type.toLowerCase()] = stageCluster;
        });
    };

    this.copyCluster = function(index) {
      $scope.stage.canaries.push(angular.copy($scope.stage.canaries[index]));
    };

    this.deleteClusterPair = function(index) {
      $scope.stage.clusterPairs.splice(index, 1);
    };
  });
