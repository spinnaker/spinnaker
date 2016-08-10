'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canaryStage', [
  require('../../../../core/application/listExtractor/listExtractor.service'),
  require('../../../../core/serverGroup/configure/common/serverGroupCommandBuilder.js'),
  require('../../../../core/cloudProvider/cloudProvider.registry.js'),
  require('../../../../core/config/settings.js'),
])
  .config(function (pipelineConfigProvider, settings) {
    if (settings.feature && settings.feature.netflixMode) {
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
            stageTypes: ['bake', 'findAmi', 'findImage'],
            message: 'You must have a Bake or Find AMI stage before a canary stage.'
          },
        ],
      });
    }
  })
  .controller('CanaryStageCtrl', function ($scope, $uibModal, stage, _,
                                           namingService, providerSelectionService,
                                           authenticationService, cloudProviderRegistry,
                                           serverGroupCommandBuilder, awsServerGroupTransformer, accountService, appListExtractorService) {

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
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig.useLookback = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.useLookback || false;
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins || 0;
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig.useGlobalDataset = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.useGlobalDataset || false;
    $scope.stage.canary.canaryConfig.actionsForUnhealthyCanary = $scope.stage.canary.canaryConfig.actionsForUnhealthyCanary || [
      {action: 'DISABLE'},
      {action: 'TERMINATE', delayBeforeActionInMins: 60}
    ];

    this.recipients = $scope.stage.canary.watchers
      ? angular.isArray($scope.stage.canary.watchers)
        ? $scope.stage.canary.watchers.join(', ')
        : $scope.stage.canary.watchers
      : '';


    this.updateWatchersList = () => {
      if(this.recipients.indexOf('${') > -1) { //check if SpEL; we don't want to convert to array
        $scope.stage.canary.watchers = this.recipients;
      } else {
        $scope.stage.canary.watchers = [];
        this.recipients.split(',').forEach((email) => {
          $scope.stage.canary.watchers.push(email.trim());
        });
      }
    };

    this.terminateUnhealthyCanaryEnabled = function (isEnabled) {
      if (isEnabled === true) {
        $scope.stage.canary.canaryConfig.actionsForUnhealthyCanary = [
          {action: 'DISABLE'},
          {action: 'TERMINATE', delayBeforeActionInMins: 60}
        ];
      } else if (isEnabled === false) {
        $scope.stage.canary.canaryConfig.actionsForUnhealthyCanary = [
          {action: 'DISABLE'}
        ];
      }

      return _.find($scope.stage.canary.canaryConfig.actionsForUnhealthyCanary, function (action) {
        return action.action === 'TERMINATE';
      }) !== undefined;
    };


    this.terminateUnhealthyCanaryMinutes = function (delayBeforeActionInMins) {
      var terminateAction = _.find($scope.stage.canary.canaryConfig.actionsForUnhealthyCanary, function (action) {
        return action.action === 'TERMINATE';
      });

      if (delayBeforeActionInMins) {
        terminateAction.delayBeforeActionInMins = delayBeforeActionInMins;
      }

      return terminateAction ? terminateAction.delayBeforeActionInMins : 60;
    };

    accountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
      setClusterList();
    });


    this.notificationHours = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours.join(',');

    this.splitNotificationHours = () => {
      var hoursField = this.notificationHours || '';
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

    let clusterFilter = (cluster) => {
      return $scope.stage.baseline.account ? cluster.account === $scope.stage.baseline.account : true;
    };

    let setClusterList = () => {
      $scope.clusterList = appListExtractorService.getClusters([$scope.application], clusterFilter);
    };

    $scope.resetSelectedCluster = () => {
      $scope.stage.baseline.cluster = undefined;
      setClusterList();
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
        $uibModal.open({
          templateUrl: config.cloneServerGroupTemplateUrl,
          controller: `${config.cloneServerGroupController} as ctrl`,
          size: 'lg',
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
      $uibModal.open({
        templateUrl: config.cloneServerGroupTemplateUrl,
        controller: `${config.cloneServerGroupController} as ctrl`,
        size: 'lg',
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
  });
