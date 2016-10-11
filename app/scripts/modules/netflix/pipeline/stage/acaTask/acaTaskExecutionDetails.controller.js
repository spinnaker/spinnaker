'use strict';

import detailsSectionModule from 'core/delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.acaTask.details.controller', [
  require('angular-ui-router'),
  detailsSectionModule,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
  require('../canary/canaryDeployment/canaryDeploymentHistory.service.js')
])
  .controller('acaTaskExecutionDetailsCtrl', function ($scope, $stateParams,
                                                       executionDetailsSectionService, canaryDeploymentHistoryService, clusterFilterService) {

    $scope.configSections = ['canarySummary', 'canaryConfig', 'canaryAnalysisHistory'];

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


    $scope.loadHistory = function () {
      if ($scope.deployment.canary && $scope.deployment.canary.canaryDeployments.length > 0) {
        $scope.viewState.loadingHistory = true;
        $scope.viewState.loadingHistoryError = false;

        var canaryDeploymentId = $scope.deployment.canary.canaryDeployments[0].id;
        canaryDeploymentHistoryService.getAnalysisHistory(canaryDeploymentId).then(
          function (results) {
            $scope.analysisHistory = results;
            $scope.viewState.loadingHistory = false;
          },
          function () {
            $scope.viewState.loadingHistory = false;
            $scope.viewState.loadingHistoryError = true;
          }
        );
      } else {
        $scope.analysisHistory = [];
        $scope.viewState.loadingHistory = false;
      }
    };

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
