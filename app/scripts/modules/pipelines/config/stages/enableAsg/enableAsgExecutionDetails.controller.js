'use strict';

angular.module('spinnaker.pipelines.stage.enableAsg.executionDetails.controller', [
  'ui.router',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
])
  .controller('EnableAsgExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['enableServerGroupConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
