'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.shrinkCluster.cf.executionDetails.controller', [
    require('angular-ui-router'),
    require('../../../../core/delivery/details/executionDetailsSection.service.js'),
    require('../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('cfShrinkClusterExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['shrinkClusterConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
