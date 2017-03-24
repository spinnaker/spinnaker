'use strict';

import {EXECUTION_DETAILS_SECTION_SERVICE} from 'core/delivery/details/executionDetailsSection.service';
import {SETTINGS} from 'core/config/settings';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.bake.azure.executionDetails.controller', [
  require('angular-ui-router'),
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('azureBakeExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService,
                                                       $interpolate) {

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

  });
