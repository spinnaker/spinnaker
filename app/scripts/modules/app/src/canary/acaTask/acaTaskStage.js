'use strict';

import * as angular from 'angular';

import { AccountService, AuthenticationService, Registry, SETTINGS } from '@spinnaker/core';

import { CanaryExecutionLabel } from '../canary/CanaryExecutionLabel';
import { CANARY_CANARY_CANARYEXECUTIONSUMMARY_CONTROLLER } from '../canary/canaryExecutionSummary.controller';

export const CANARY_ACATASK_ACATASKSTAGE = 'spinnaker.canary.acaTaskStage';
export const name = CANARY_ACATASK_ACATASKSTAGE; // for backwards compatibility
angular
  .module(CANARY_ACATASK_ACATASKSTAGE, [CANARY_CANARY_CANARYEXECUTIONSUMMARY_CONTROLLER])
  .config(function () {
    if (SETTINGS.feature.canary) {
      Registry.pipeline.registerStage({
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
        validators: [],
      });
    }
  })
  .controller('AcaTaskStageCtrl', [
    '$scope',
    '$uibModal',
    'stage',
    function ($scope, $uibModal, stage) {
      const user = AuthenticationService.getAuthenticatedUser();
      $scope.stage = stage;
      $scope.stage.baseline = $scope.stage.baseline || {};
      $scope.stage.canary = $scope.stage.canary || {};
      $scope.stage.canary.application = $scope.stage.canary.application || $scope.application.name;
      $scope.stage.canary.owner = $scope.stage.canary.owner || (user.authenticated ? user.name : null);
      $scope.stage.canary.watchers = $scope.stage.canary.watchers || [];
      $scope.stage.canary.canaryConfig = $scope.stage.canary.canaryConfig || {
        name: [$scope.pipeline.name, 'Canary'].join(' - '),
      };
      $scope.stage.canary.canaryConfig.canaryHealthCheckHandler = Object.assign(
        $scope.stage.canary.canaryConfig.canaryHealthCheckHandler || {},
        { '@class': 'com.netflix.spinnaker.mine.CanaryResultHealthCheckHandler' },
      );
      $scope.stage.canary.canaryConfig.canaryAnalysisConfig =
        $scope.stage.canary.canaryConfig.canaryAnalysisConfig || {};
      $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours =
        $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours || [];
      $scope.stage.canary.canaryConfig.canaryAnalysisConfig.useLookback =
        $scope.stage.canary.canaryConfig.canaryAnalysisConfig.useLookback || false;
      $scope.stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins =
        $scope.stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins || 0;

      $scope.stage.canary.canaryDeployments = $scope.stage.canary.canaryDeployments || [
        { type: 'query', '@class': '.CanaryTaskDeployment' },
      ];

      $scope.canaryDeployment = $scope.stage.canary.canaryDeployments[0];

      this.recipients = $scope.stage.canary.watchers
        ? angular.isArray($scope.stage.canary.watchers) //if array, convert to comma separated string
          ? $scope.stage.canary.watchers.join(', ')
          : $scope.stage.canary.watchers //if it is not an array it is probably a SpEL
        : '';

      const applicationProviders = $scope.application.attributes.cloudProviders;
      $scope.accounts = [];
      $scope.regions = [];

      if (applicationProviders.length === 0) {
        applicationProviders[0] = 'aws'; // default to AWS if no provider is set
      }

      applicationProviders.forEach((p) => {
        AccountService.listAccounts(p).then((a) => ($scope.accounts = $scope.accounts.concat(a)));
        AccountService.getUniqueAttributeForAllAccounts(p, 'regions').then(
          (r) => ($scope.regions = $scope.regions.concat(r)),
        );
      });

      //TODO: Extract to be reusable with canaryStage [zkt]
      this.updateWatchersList = () => {
        if (this.recipients.includes('${')) {
          //check if SpEL; we don't want to convert to array
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
        const hoursField = this.notificationHours || '';
        $scope.stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours = _.map(
          hoursField.split(','),
          function (str) {
            if (!parseInt(str.trim()).isNaN) {
              return parseInt(str.trim());
            }
            return 0;
          },
        );
      };
    },
  ]);
