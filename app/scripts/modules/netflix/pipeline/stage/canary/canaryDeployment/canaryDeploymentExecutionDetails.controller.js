'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.canaryDeployment.details.controller', [
  require('angular-ui-router'),
  require('../../../../../core/utils/lodash.js'),
  require('../../../../../core/delivery/details/executionDetailsSection.service.js'),
  require('../../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
  require('../../../../../core/navigation/urlBuilder.service.js'),
  require('./canaryDeploymentHistory.service.js')
])
  .controller('CanaryDeploymentExecutionDetailsCtrl', function ($scope, _, $stateParams, $timeout,
                                                                executionDetailsSectionService,
                                                                canaryDeploymentHistoryService, urlBuilderService,
                                                                clusterFilterService) {

    function initialize() {
      $scope.configSections = ['canaryDeployment', 'canaryAnalysisHistory'];

      if ($scope.stage.context && $scope.stage.context.commits && $scope.stage.context.commits.length > 0) {
        $scope.configSections.push('codeChanges');
      }

      $scope.deployment = $scope.stage.context;
      $scope.viewState = {
        loadingHistory: true,
        loadingHistoryError: false,
      };

      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;

      $scope.commits = $scope.stage.context.commits;

      if ($scope.deployment.baselineCluster) {
        var baselineMetadata = {
          type: 'clusters',
          application: $scope.stage.context.application,
          cluster: $scope.deployment.baselineCluster.name,
          account: $scope.deployment.baselineCluster.accountName,
        };
        baselineMetadata.href = urlBuilderService.buildFromMetadata(baselineMetadata);
        $scope.baselineClusterUrl = baselineMetadata;

        var canaryMetadata = {
          type: 'clusters',
          application: $scope.stage.context.application,
          cluster: $scope.deployment.canaryCluster.name,
          account: $scope.deployment.canaryCluster.accountName,
        };
        canaryMetadata.href = urlBuilderService.buildFromMetadata(canaryMetadata);
        $scope.canaryClusterUrl = canaryMetadata;

        $scope.loadHistory();
      }
    }

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

    $scope.$on('$stateChangeSuccess',
      function () {
        $timeout(initialize);
      },
      true);

  });
