'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.disableCluster.cf.executionDetails.controller', [
    require('angular-ui-router'),
    require('../../../../core/delivery/details/executionDetailsSection.service.js'),
    require('../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
])
.controller('cfDisableClusterExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['disableClusterConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
