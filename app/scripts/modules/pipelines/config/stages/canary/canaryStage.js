'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.canaryStage', [
  require('../../../../amazon/serverGroup/configure/serverGroupCommandBuilder.service.js'),
  require('../../../../core/cloudProvider/cloudProvider.registry.js'),
])
  .config(function (pipelineConfigProvider, settings) {
    if (settings.feature.canary === true) {
        pipelineConfigProvider.registerStage({
          label: 'Canary',
          description: 'Canary tests new changes against a baseline version',
          key: 'canary',
          templateUrl: require('./canaryStage.html'),
          executionDetailsUrl: require('./canaryExecutionDetails.html'),
          executionSummaryUrl: require('./canaryExecutionSummary.html'),
          executionLabelTemplateUrl: require('./canaryExecutionLabel.html'),
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
  .controller('CanaryStageCtrl', function ($scope, $modal, stage, _,
                                           namingService, providerSelectionService,
                                           authenticationService, cloudProviderRegistry,
                                           serverGroupCommandBuilder, awsServerGroupTransformer, accountService) {

    var user = authenticationService.getAuthenticatedUser();
    $scope.stage = stage;
    $scope.stage.baseline = $scope.stage.baseline || {};
    $scope.stage.scaleUp = $scope.stage.scaleUp || {};
    $scope.stage.canary = $scope.stage.canary || {};
    $scope.stage.canary.owner = $scope.stage.canary.owner || (user.authenticated ? user.name : null);
    $scope.stage.canary.watchers = $scope.stage.canary.watchers || [];
    $scope.stage.canary.canaryConfig = $scope.stage.canary.canaryConfig || { name: [$scope.pipeline.name, 'Canary'].join(' - ') };
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig = $scope.stage.canary.canaryConfig.canaryAnalysisConfig || {};
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours || [];

    accountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
    });

    $scope.notificationHours = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours.join(',');

    this.splitNotificationHours = function() {
      var hoursField = $scope.notificationHours || '';
      $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours = _.map(hoursField.split(','), function(str) {
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
    }

    function configureServerGroupCommandForEditing(command) {
      command.viewState.disableStrategySelection = true;
      command.viewState.hideClusterNamePreview = true;
      command.viewState.readOnlyFields = { credentials: true, region: true, subnet: true };
      delete command.strategy;
    }

    this.addClusterPair = function() {
      $scope.stage.clusterPairs = $scope.stage.clusterPairs || [];
      providerSelectionService.selectProvider($scope.application).then(function(selectedProvider) {
        let config = cloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
        $modal.open({
          templateUrl: config.cloneServerGroupTemplateUrl,
          controller: `${config.cloneServerGroupController} as ctrl`,
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
                  command.viewState.overrides = {
                    capacity: {
                      min: 1, max: 1, desired: 1,
                    }
                  };
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
      let config = cloudProviderRegistry.getValue(cluster.provider, 'serverGroup');
      $modal.open({
        templateUrl: config.cloneServerGroupTemplateUrl,
        controller: `${config.cloneServerGroupController} as ctrl`,
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

    this.deleteClusterPair = function(index) {
      $scope.stage.clusterPairs.splice(index, 1);
    };
  }).name;
