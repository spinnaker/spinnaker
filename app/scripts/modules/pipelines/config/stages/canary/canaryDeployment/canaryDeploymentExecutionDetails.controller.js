'use strict';

angular.module('deckApp.pipelines.stage.canary.canaryDeployment.details.controller', [
  'ui.router',
  'deckApp.utils.lodash',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
  'deckApp.urlBuilder',
])
  .controller('CanaryDeploymentExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService, urlBuilder) {

    function initialize() {
      $scope.configSections = ['canaryDeployment'];

      $scope.deployment = $scope.stage.context;

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

    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
