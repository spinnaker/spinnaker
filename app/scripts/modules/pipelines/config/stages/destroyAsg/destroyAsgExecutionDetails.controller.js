'use strict';

angular.module('deckApp.pipelines.stage.destroyAsg.executionDetails.controller', [
  'ui.router',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('DestroyAsgExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['destroyAsgConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
