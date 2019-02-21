'use strict';

const angular = require('angular');

import { SETTINGS } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.pipeline.stage.bake.executionDetails.controller', [require('@uirouter/angularjs').default])
  .controller('azureBakeExecutionDetailsCtrl', ['$scope', '$stateParams', 'executionDetailsSectionService', '$interpolate', function(
    $scope,
    $stateParams,
    executionDetailsSectionService,
    $interpolate,
  ) {
    $scope.configSections = ['bakeConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.provider = $scope.stage.context.cloudProviderType || 'azure';
      $scope.roscoMode = SETTINGS.feature.roscoMode;
      $scope.bakeryDetailUrl = $interpolate(SETTINGS.bakeryDetailUrl);
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  }]);
