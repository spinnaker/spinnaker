'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { ClusterState, SETTINGS } from '@spinnaker/core';

import { CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE } from '../canary/canaryDeployment/canaryDeploymentHistory.service';

export const CANARY_ACATASK_ACATASKEXECUTIONDETAILS_CONTROLLER = 'spinnaker.canary.acaTask.details.controller';
export const name = CANARY_ACATASK_ACATASKEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
module(CANARY_ACATASK_ACATASKEXECUTIONDETAILS_CONTROLLER, [
  UIROUTER_ANGULARJS,
  CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE,
]).controller('acaTaskExecutionDetailsCtrl', [
  '$scope',
  '$stateParams',
  'executionDetailsSectionService',
  'canaryDeploymentHistoryService',
  function ($scope, $stateParams, executionDetailsSectionService, canaryDeploymentHistoryService) {
    $scope.configSections = ['canarySummary', 'canaryConfig', 'canaryAnalysisHistory'];

    $scope.queryListUrl = SETTINGS.canaryConfig ? SETTINGS.canaryConfig.queryListUrl : null;

    const initialized = () => {
      $scope.detailsSection = $stateParams.details;

      $scope.canary = $scope.stage.context.canary;

      if ($scope.canary) {
        $scope.canaryConfig = $scope.canary.canaryConfig;
        $scope.baseline = $scope.stage.context.baseline;
        $scope.canaryDeployments = $scope.canary.canaryDeployments;
      }

      $scope.deployment = $scope.stage.context;

      $scope.viewState = {
        loadingHistory: true,
        loadingHistoryError: false,
      };

      $scope.detailsSection = $stateParams.details;

      $scope.loadHistory();
    };

    $scope.loadHistory = function () {
      if (
        $scope.deployment.canary &&
        $scope.deployment.canary.canaryDeployments &&
        $scope.deployment.canary.canaryDeployments.length > 0
      ) {
        $scope.viewState.loadingHistory = true;
        $scope.viewState.loadingHistoryError = false;

        const canaryDeploymentId = $scope.deployment.canary.canaryDeployments[0].id;
        canaryDeploymentHistoryService.getAnalysisHistory(canaryDeploymentId).then(
          function (results) {
            $scope.analysisHistory = results;
            $scope.viewState.loadingHistory = false;
          },
          function () {
            $scope.viewState.loadingHistory = false;
            $scope.viewState.loadingHistoryError = true;
          },
        );
      } else {
        $scope.analysisHistory = [];
        $scope.viewState.loadingHistory = false;
      }
    };

    this.overrideFiltersForUrl = (r) => ClusterState.filterService.overrideFiltersForUrl(r);

    const initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  },
]);
