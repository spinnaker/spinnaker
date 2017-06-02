'use strict';

import {
  EXECUTION_DETAILS_SECTION_SERVICE,
  SETTINGS
} from '@spinnaker/core';

const angular = require('angular');

module.exports = angular.module('spinnaker.oraclebmcs.pipeline.stage.bake.executionDetails.controller', [
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('@uirouter/angularjs').default
])
  .controller('oraclebmcsBakeExecutionDetailsCtrl', function ($log, $scope, $stateParams, executionDetailsSectionService, $interpolate) {

    $scope.configSections = ['bakeConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.provider = $scope.stage.context.cloudProviderType || 'oraclebmcs';
      $scope.roscoMode = SETTINGS.feature.roscoMode;
      $scope.bakeryDetailUrl = $interpolate(SETTINGS.bakeryDetailUrl);
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  });
