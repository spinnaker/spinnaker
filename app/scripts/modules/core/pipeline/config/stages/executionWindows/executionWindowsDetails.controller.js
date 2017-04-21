'use strict';

import {EXECUTION_DETAILS_SECTION_SERVICE} from 'core/delivery/details/executionDetailsSection.service';
import {EXECUTION_WINDOW_ACTIONS_COMPONENT} from './executionWindowActions.component';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindows.details.controller', [
  require('angular-ui-router'),
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
  EXECUTION_WINDOW_ACTIONS_COMPONENT
])
  .controller('ExecutionWindowsDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['windowConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
