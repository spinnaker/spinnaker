'use strict';

const angular = require('angular');

import { CLUSTER_FILTER_SERVICE, URL_BUILDER_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.canary.canaryDeployment.details.controller', [
  require('@uirouter/angularjs').default,
  CLUSTER_FILTER_SERVICE,
  URL_BUILDER_SERVICE,
  require('./canaryDeploymentHistory.service.js')
])
  .controller('CanaryDeploymentExecutionDetailsCtrl', function ($scope, $stateParams,
                                                                executionDetailsSectionService,
                                                                canaryDeploymentHistoryService, urlBuilderService,
                                                                clusterFilterService) {

    $scope.configSections = ['canaryDeployment', 'canaryAnalysisHistory'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;

      if ($scope.stage.context && $scope.stage.context.commits && $scope.stage.context.commits.length > 0) {
        if(!$scope.configSections.includes('codeChanges')) {
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
        var baselineMetadata = {
          type: 'clusters',
          application: $scope.stage.context.application,
          cluster: $scope.deployment.baselineCluster.name,
          account: $scope.deployment.baselineCluster.accountName,
          project: $stateParams.project,
        };
        baselineMetadata.href = urlBuilderService.buildFromMetadata(baselineMetadata);
        $scope.baselineClusterUrl = baselineMetadata;

        var canaryMetadata = {
          type: 'clusters',
          application: $scope.stage.context.application,
          cluster: $scope.deployment.canaryCluster.name,
          account: $scope.deployment.canaryCluster.accountName,
          project: $stateParams.project,
        };
        canaryMetadata.href = urlBuilderService.buildFromMetadata(canaryMetadata);
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
          }
        );
      } else {
        $scope.analysisHistory = [];
        $scope.viewState.loadingHistory = false;
      }
    };

    this.overrideFiltersForUrl = r => clusterFilterService.overrideFiltersForUrl(r);

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
