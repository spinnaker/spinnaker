'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.canary.details.controller', [
  require('angular-ui-router'),
  require('../../../../utils/lodash.js'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js')
])
  .controller('CanaryExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService) {

    function initialize() {
      $scope.configSections = ['canarySummary', 'canaryConfig', 'taskStatus'];
      $scope.canary = $scope.stage.context.canary;
      $scope.canaryConfig = $scope.stage.context.canary.canaryConfig;
      $scope.baseline = $scope.stage.context.baseline;
      $scope.canaryDeployments = $scope.stage.context.canary.canaryDeployments;

      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
