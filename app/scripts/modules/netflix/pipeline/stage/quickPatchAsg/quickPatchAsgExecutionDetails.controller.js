'use strict';

import detailsSectionModule from '../../../../core/delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.quickPatchAsg.executionDetails.controller', [
  require('angular-ui-router'),
  detailsSectionModule,
  require('../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('QuickPatchAsgExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['quickPatchServerGroupConfig'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
