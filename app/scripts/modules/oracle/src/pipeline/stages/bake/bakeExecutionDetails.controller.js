'use strict';

import { EXECUTION_DETAILS_SECTION_SERVICE, SETTINGS } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.oracle.pipeline.stage.bake.executionDetails.controller', [
    EXECUTION_DETAILS_SECTION_SERVICE,
    require('@uirouter/angularjs').default,
  ])
  .controller('oracleBakeExecutionDetailsCtrl', [
    '$log',
    '$scope',
    '$stateParams',
    'executionDetailsSectionService',
    '$interpolate',
    function($log, $scope, $stateParams, executionDetailsSectionService, $interpolate) {
      $scope.configSections = ['bakeConfig', 'taskStatus'];

      let initialized = () => {
        $scope.detailsSection = $stateParams.details;
        $scope.provider = $scope.stage.context.cloudProviderType || 'oracle';
        $scope.roscoMode = SETTINGS.feature.roscoMode;
        $scope.bakeryDetailUrl = $interpolate(SETTINGS.bakeryDetailUrl);
      };

      let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

      initialize();

      $scope.$on('$stateChangeSuccess', initialize);
    },
  ]);
