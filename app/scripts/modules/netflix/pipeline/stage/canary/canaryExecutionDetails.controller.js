'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.details.controller', [
  require('angular-ui-router').default,
])
  .controller('CanaryExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['canarySummary', 'canaryConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.canary = $scope.stage.context.canary;

      if ($scope.canary) {
        $scope.canaryConfig = $scope.canary.canaryConfig;
        $scope.baseline = $scope.stage.context.baseline;
        $scope.canaryDeployments = $scope.canary.canaryDeployments;
      }
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
