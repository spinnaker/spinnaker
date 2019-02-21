'use strict';

const angular = require('angular');

import { SETTINGS } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.titus.pipeline.stage.bake.executionDetails.controller', [require('@uirouter/angularjs').default])
  .controller('titusBakeExecutionDetailsCtrl', [
    '$scope',
    '$stateParams',
    'executionDetailsSectionService',
    '$interpolate',
    function($scope, $stateParams, executionDetailsSectionService, $interpolate) {
      $scope.configSections = ['bakeConfig', 'taskStatus'];

      let initialized = () => {
        $scope.detailsSection = $stateParams.details;
        $scope.provider = $scope.stage.context.cloudProviderType || 'titus';
        $scope.bakeryDetailUrl = $interpolate(SETTINGS.bakeryDetailUrl);
      };

      let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

      initialize();

      $scope.$on('$stateChangeSuccess', initialize);
    },
  ]);
