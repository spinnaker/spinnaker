'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { ClusterState, UrlBuilder } from '@spinnaker/core';

import { CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE } from './canaryDeploymentHistory.service';

export const CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTEXECUTIONDETAILS_CONTROLLER =
  'spinnaker.canary.canaryDeployment.details.controller';
export const name = CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
module(CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTEXECUTIONDETAILS_CONTROLLER, [
  UIROUTER_ANGULARJS,
  CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTHISTORY_SERVICE,
]).controller('CanaryDeploymentExecutionDetailsCtrl', [
  '$scope',
  '$stateParams',
  'executionDetailsSectionService',
  'canaryDeploymentHistoryService',
  function ($scope, $stateParams, executionDetailsSectionService, canaryDeploymentHistoryService) {
    $scope.configSections = ['canaryDeployment', 'canaryAnalysisHistory'];

    const initialized = () => {
      $scope.detailsSection = $stateParams.details;

      if ($scope.stage.context && $scope.stage.context.commits && $scope.stage.context.commits.length > 0) {
        if (!$scope.configSections.includes('codeChanges')) {
          $scope.configSections.push('codeChanges');
        }
      }

      $scope.deployment = $scope.stage.context;
      $scope.viewState = {
        loadingHistory: true,
        loadingHistoryError: false,
      };

      $scope.commits = $scope.stage.context.commits;

      if ($scope.deployment.baselineCluster) {
        const baselineMetadata = {
          type: 'clusters',
          application: $scope.stage.context.application,
          cluster: $scope.deployment.baselineCluster.name,
          account: $scope.deployment.baselineCluster.accountName,
          project: $stateParams.project,
        };
        baselineMetadata.href = UrlBuilder.buildFromMetadata(baselineMetadata);
        $scope.baselineClusterUrl = baselineMetadata;

        const canaryMetadata = {
          type: 'clusters',
          application: $scope.stage.context.application,
          cluster: $scope.deployment.canaryCluster.name,
          account: $scope.deployment.canaryCluster.accountName,
          project: $stateParams.project,
        };
        canaryMetadata.href = UrlBuilder.buildFromMetadata(canaryMetadata);
        $scope.canaryClusterUrl = canaryMetadata;

        $scope.loadHistory();
      }
    };

    $scope.loadHistory = function () {
      if ($scope.deployment.canaryDeploymentId) {
        $scope.viewState.loadingHistory = true;
        $scope.viewState.loadingHistoryError = false;

        canaryDeploymentHistoryService.getAnalysisHistory($scope.deployment.canaryDeploymentId).then(
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
