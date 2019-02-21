'use strict';

const angular = require('angular');

import { SETTINGS } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.gce.pipeline.stage.bake.executionDetails.controller', [require('@uirouter/angularjs').default])
  .controller('gceBakeExecutionDetailsCtrl', ['$scope', '$stateParams', 'executionDetailsSectionService', '$interpolate', function(
    $scope,
    $stateParams,
    executionDetailsSectionService,
    $interpolate,
  ) {
    $scope.configSections = ['bakeConfig', 'taskStatus', 'artifactStatus'];

    const initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.provider = $scope.stage.context.cloudProviderType || 'gce';
      $scope.roscoMode = SETTINGS.feature.roscoMode;
      $scope.bakeryDetailUrl = $interpolate(SETTINGS.bakeryDetailUrl);
    };

    const initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  }]);
