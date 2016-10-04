'use strict';

import detailsSectionModule from '../../../../../delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.destroyAsg.executionDetails.controller', [
  require('angular-ui-router'),
  detailsSectionModule,
  require('../../../../../delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('destroyAsgExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['destroyServerGroupConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
