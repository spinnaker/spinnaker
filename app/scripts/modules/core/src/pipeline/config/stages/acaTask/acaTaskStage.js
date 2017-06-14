'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE, CLOUD_PROVIDER_REGISTRY, SETTINGS } from '@spinnaker/core';

import { CanaryExecutionLabel } from '../canary/CanaryExecutionLabel';

module.exports = angular.module('spinnaker.core.pipeline.stage.acaTaskStage', [
  CLOUD_PROVIDER_REGISTRY,
  require('../canary/canaryExecutionSummary.controller'),
  ACCOUNT_SERVICE,
])
  .config(function (pipelineConfigProvider) {
    if (SETTINGS.feature.canary) {
      pipelineConfigProvider.registerStage({
        label: 'ACA Task',
        description: 'Runs a canary task against an existing cluster, asg, or query',
        key: 'acaTask',
        restartable: true,
        templateUrl: require('./acaTaskStage.html'),
        executionDetailsUrl: require('./acaTaskExecutionDetails.html'),
        executionSummaryUrl: require('./acaTaskExecutionSummary.html'),
        stageFilter: (stage) => ['monitorAcaTask', 'acaTask'].includes(stage.type),
        executionLabelComponent: CanaryExecutionLabel,
        controller: 'AcaTaskStageCtrl',
        controllerAs: 'acaTaskStageCtrl',
        validators: [
        ],
      });
    }
  })
  .controller('AcaTaskStageCtrl', function ($scope, $uibModal, stage,
                                           namingService, providerSelectionService,
                                           authenticationService, cloudProviderRegistry,
                                           awsServerGroupTransformer, accountService) {

    var user = authenticationService.getAuthenticatedUser();
    $scope.stage = stage;
    $scope.stage.baseline = $scope.stage.baseline || {};
    $scope.stage.canary = $scope.stage.canary || {};
    $scope.stage.canary.application = $scope.stage.canary.application || $scope.application.name;
    $scope.stage.canary.owner = $scope.stage.canary.owner || (user.authenticated ? user.name : null);
    $scope.stage.canary.watchers = $scope.stage.canary.watchers || [];
    $scope.stage.canary.canaryConfig = $scope.stage.canary.canaryConfig || { name: [$scope.pipeline.name, 'Canary'].join(' - ') };
    $scope.stage.canary.canaryConfig.canaryHealthCheckHandler = Object.assign($scope.stage.canary.canaryConfig.canaryHealthCheckHandler || {}, {'@class':'com.netflix.spinnaker.mine.CanaryResultHealthCheckHandler'});
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig = $scope.stage.canary.canaryConfig.canaryAnalysisConfig || {};
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours || [];
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig.useLookback = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.useLookback || false;
    $scope.stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins = $scope.stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins || 0;

    $scope.stage.canary.canaryDeployments = $scope.stage.canary.canaryDeployments || [{type: 'query', '@class':'.CanaryTaskDeployment'}];

    $scope.canaryDeployment = $scope.stage.canary.canaryDeployments[0];

    this.recipients = $scope.stage.canary.watchers
      ? angular.isArray($scope.stage.canary.watchers) //if array, convert to comma separated string
        ? $scope.stage.canary.watchers.join(', ')
        : $scope.stage.canary.watchers //if it is not an array it is probably a SpEL
      : '';

    accountService.getUniqueAttributeForAllAccounts('aws', 'regions')
      .then( (regions) => {
        $scope.regions = regions.sort();
      });


    accountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
    });


    //TODO: Extract to be reusable with canaryStage [zkt]
    this.updateWatchersList = () => {
      if (this.recipients.includes('${')) { //check if SpEL; we don't want to convert to array
        $scope.stage.canary.watchers = this.recipients;
      } else {
        $scope.stage.canary.watchers = [];
        this.recipients.split(',').forEach((email) => {
          $scope.stage.canary.watchers.push(email.trim());
        });
      }
    };

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

  });
