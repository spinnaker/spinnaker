'use strict';

angular.module('spinnaker.pipelines.stage.bake.executionDetails.controller', [
  'ui.router',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
])
  .controller('BakeExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, $timeout) {

    $scope.configSections = ['bakeConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update any scope values based on the stage
      $timeout(function() {
        $scope.provider = $scope.stage.context.cloudProviderType || 'aws';
      });
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
