'use strict';

angular.module('deckApp.pipelines.stage.modifyScalingProcess.executionDetails.controller', [
  'ui.router',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('ModifyScalingProcessExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['modifyScalingProcessesConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
