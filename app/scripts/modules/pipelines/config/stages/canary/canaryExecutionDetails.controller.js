'use strict';

angular.module('deckApp.pipelines.stage.canary.details.controller', [
  'ui.router',
  'deckApp.utils.lodash',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive'
])
  .controller('CanaryExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService) {

    function initialize() {
      $scope.configSections = ['canarySummary', 'canaryConfig', 'taskStatus'];
      $scope.canary = $scope.stage.context.canary;
      $scope.canaryConfig = $scope.stage.context.canary.canaryConfig;
      $scope.baseline = $scope.stage.context.baseline;
      $scope.canaryDeployments = $scope.stage.context.canary.canaryDeployments;

      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
