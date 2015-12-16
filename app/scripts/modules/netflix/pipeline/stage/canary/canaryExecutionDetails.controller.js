'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.details.controller', [
  require('angular-ui-router'),
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/delivery/details/executionDetailsSection.service.js'),
  require('../../../../core/delivery/details/executionDetailsSectionNav.directive.js')
])
  .controller('CanaryExecutionDetailsCtrl', function ($scope, _, $stateParams, $timeout, executionDetailsSectionService) {

    $scope.configSections = ['canarySummary', 'canaryConfig', 'taskStatus'];

    function initialize() {

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update any scope values based on the stage
      $timeout(function() {
        executionDetailsSectionService.synchronizeSection($scope.configSections);
        $scope.detailsSection = $stateParams.details;

        $scope.canary = $scope.stage.context.canary;
        if ($scope.canary) {
          $scope.canaryConfig = $scope.canary.canaryConfig;
          $scope.baseline = $scope.stage.context.baseline;
          $scope.canaryDeployments = $scope.canary.canaryDeployments;
        }
      });
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
