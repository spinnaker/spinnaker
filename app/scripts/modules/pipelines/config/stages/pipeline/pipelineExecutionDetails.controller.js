'use strict';

angular.module('spinnaker.pipelines.stage.pipeline.executionDetails.controller', [
  'ui.router',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
])
  .controller('pipelineExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['pipelineConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
