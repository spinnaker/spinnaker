'use strict';

const angular = require('angular');

import { ClusterState, SETTINGS } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.canary.acaTask.details.controller', [
    require('@uirouter/angularjs').default,
    require('../canary/canaryDeployment/canaryDeploymentHistory.service').name,
  ])
  .controller('acaTaskExecutionDetailsCtrl', ['$scope', '$stateParams', 'executionDetailsSectionService', 'canaryDeploymentHistoryService', function(
    $scope,
    $stateParams,
    executionDetailsSectionService,
    canaryDeploymentHistoryService,
  ) {
    $scope.configSections = ['canarySummary', 'canaryConfig', 'canaryAnalysisHistory'];

    $scope.queryListUrl = SETTINGS.canaryConfig ? SETTINGS.canaryConfig.queryListUrl : null;

    let initialized = () => {
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

    $scope.loadHistory = function() {
      if (
        $scope.deployment.canary &&
        $scope.deployment.canary.canaryDeployments &&
        $scope.deployment.canary.canaryDeployments.length > 0
      ) {
        $scope.viewState.loadingHistory = true;
        $scope.viewState.loadingHistoryError = false;

        var canaryDeploymentId = $scope.deployment.canary.canaryDeployments[0].id;
        canaryDeploymentHistoryService.getAnalysisHistory(canaryDeploymentId).then(
          function(results) {
            $scope.analysisHistory = results;
            $scope.viewState.loadingHistory = false;
          },
          function() {
            $scope.viewState.loadingHistory = false;
            $scope.viewState.loadingHistoryError = true;
          },
        );
      } else {
        $scope.analysisHistory = [];
        $scope.viewState.loadingHistory = false;
      }
    };

    this.overrideFiltersForUrl = r => ClusterState.filterService.overrideFiltersForUrl(r);

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  }]);
