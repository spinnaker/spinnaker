'use strict';

angular.module('spinnaker.pipelines.stage.canary.canaryDeployment.details.controller', [
  'ui.router',
  'spinnaker.utils.lodash',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
  'spinnaker.urlBuilder',
  'spinnaker.pipelines.stages.canary.deployment.history.service'
])
  .controller('CanaryDeploymentExecutionDetailsCtrl', function ($scope, _, $stateParams, $timeout,
                                                                executionDetailsSectionService,
                                                                canaryDeploymentHistoryService, urlBuilder) {

    function initialize() {
      $scope.configSections = ['canaryDeployment', 'canaryAnalysisHistory', 'codeChanges'];

      $scope.deployment = $scope.stage.context;
      $scope.viewState = {
        loadingHistory: true,
        loadingHistoryError: false,
      };

      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;

      $scope.commits = $scope.stage.context.commits;

      if ($scope.deployment.baselineCluster) {
        $scope.baselineClusterUrl = urlBuilder.buildFromMetadata({
          type: 'clusters',
          application: $scope.stage.context.application,
          cluster: $scope.deployment.baselineCluster.name,
          account: $scope.deployment.baselineCluster.accountName,
        });

        $scope.canaryClusterUrl = urlBuilder.buildFromMetadata({
          type: 'clusters',
          application: $scope.stage.context.application,
          cluster: $scope.deployment.canaryCluster.name,
          account: $scope.deployment.canaryCluster.accountName,
        });

        $scope.loadHistory();
      }
    }

    $scope.loadHistory = function() {
      if ($scope.deployment.canaryDeploymentId) {
        $scope.viewState.loadingHistory = true;
        $scope.viewState.loadingHistoryError = false;

        canaryDeploymentHistoryService.getAnalysisHistory($scope.deployment.canaryDeploymentId).then(
          function(results) {
            $scope.analysisHistory = results;
            $scope.viewState.loadingHistory = false;
          },
          function() {
            $scope.viewState.loadingHistory = false;
            $scope.viewState.loadingHistoryError = true;
          }
        );
      } else {
        $scope.analysisHistory = [];
        $scope.viewState.loadingHistory = false;
      }
    };

    initialize();

    $scope.$on('$stateChangeSuccess',
      function() { $timeout(initialize); },
    true);

  });
