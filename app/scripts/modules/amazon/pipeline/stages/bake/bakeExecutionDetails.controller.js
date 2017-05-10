'use strict';

import {EXECUTION_DETAILS_SECTION_SERVICE} from 'core/delivery/details/executionDetailsSection.service';
import {SETTINGS} from 'core/config/settings';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.bake.aws.executionDetails.controller', [
  require('angular-ui-router').default,
  EXECUTION_DETAILS_SECTION_SERVICE,
])
  .controller('awsBakeExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService,
                                                       $interpolate) {

    $scope.configSections = ['bakeConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.provider = $scope.stage.context.cloudProviderType || 'aws';
      $scope.roscoMode = SETTINGS.feature.roscoMode;
      $scope.bakeryDetailUrl = $interpolate(SETTINGS.bakeryDetailUrl);
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
