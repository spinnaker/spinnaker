'use strict';

angular.module('deckApp.pipelines.stage.canary.canaryDeployment.details.controller', [
  'ui.router',
  'deckApp.utils.lodash',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('CanaryDeploymentExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService) {

    function initialize() {
      $scope.configSections = ['canaryDeployment'];

      $scope.deployment = $scope.stage.context;

      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
