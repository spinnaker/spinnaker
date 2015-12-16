'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.scaleDownCluster.aws.executionDetails.controller', [
    require('angular-ui-router'),
    require('../../../../../delivery/details/executionDetailsSection.service.js'),
    require('../../../../../delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('awsScaleDownClusterExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['scaleDownClusterConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
