'use strict';

angular.module('deckApp.pipelines.stage.wait.executionDetails.controller', [
  'ui.router',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('WaitExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['waitConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
