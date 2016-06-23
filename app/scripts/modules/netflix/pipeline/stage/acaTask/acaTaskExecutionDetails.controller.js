'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.acaTask.details.controller', [
  require('angular-ui-router'),
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/delivery/details/executionDetailsSection.service.js'),
  require('../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
  require('../canary/canaryDeployment/canaryDeploymentHistory.service.js')
])
  .controller('acaTaskExecutionDetailsCtrl', function ($scope, _, $stateParams, $timeout,
                                                       executionDetailsSectionService, canaryDeploymentHistoryService, clusterFilterService) {

    $scope.configSections = ['canarySummary', 'canaryConfig', 'canaryAnalysisHistory'];

    function initialize() {

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update any scope values based on the stage
      $timeout(function() {
        executionDetailsSectionService.synchronizeSection($scope.configSections);
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

        executionDetailsSectionService.synchronizeSection($scope.configSections);
        $scope.detailsSection = $stateParams.details;


        $scope.loadHistory();
      });

    }


    $scope.loadHistory = function () {

      if ($scope.deployment.canary.canaryDeployments.length > 0) {
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

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
