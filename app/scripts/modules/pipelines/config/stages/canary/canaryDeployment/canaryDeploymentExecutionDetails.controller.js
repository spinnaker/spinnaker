'use strict';

angular.module('deckApp.pipelines.stage.canary.canaryDeployment.details.controller', [
  'ui.router',
  'deckApp.utils.lodash',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
  'deckApp.urlBuilder',
  'deckApp.pipelines.stages.canary.deployment.history.service'
])
  .controller('CanaryDeploymentExecutionDetailsCtrl', function ($scope, _, $stateParams,
                                                                executionDetailsSectionService,
                                                                canaryDeploymentHistoryService, urlBuilder) {

    function initialize() {
      $scope.configSections = ['canaryDeployment', 'canaryAnalysisHistory'];

      $scope.deployment = $scope.stage.context;
      $scope.viewState = {
        loadingHistory: true,
        loadingHistoryError: false,
      };

      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;

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
        $scope.viewState.loadingHistory = false;
      }
    };


    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
