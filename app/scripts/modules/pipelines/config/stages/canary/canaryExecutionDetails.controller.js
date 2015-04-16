'use strict';

angular.module('deckApp.pipelines.stage.canary.details.controller', [
  'ui.router',
  'deckApp.utils.lodash',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('CanaryExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService) {

    function initialize() {
      var step = parseInt($stateParams.step);

      if (!step) {
        $scope.configSections = ['canary', 'taskStatus'];
        $scope.canaryConfig = $scope.stage.context.canary.canaryConfig;
      } else {
        $scope.configSections = ['canaryDeployment'];
        $scope.deployment = $scope.stage.context.canary.canaryDeployments[step-1];
      }

      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
