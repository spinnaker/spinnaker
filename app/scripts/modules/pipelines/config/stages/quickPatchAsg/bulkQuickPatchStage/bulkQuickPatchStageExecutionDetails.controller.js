'use strict';

angular.module('spinnaker.pipelines.stage.quickPatchAsg.bulkQuickPatchStage.executionDetails.controller', [
  'ui.router',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
])
  .controller('BulkQuickPatchAsgExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['bulkQuickPatchStageConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
