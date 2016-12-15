'use strict';

import _ from 'lodash';
import {EXECUTION_DETAILS_SECTION_SERVICE} from 'core/delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.property.details.controller', [
  require('angular-ui-router'),
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js')
])
  .controller('PropertyExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['propertiesConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.properties = $scope.stage.context.persistedProperties;
      $scope.notificationEmail = $scope.stage.context.email;
      $scope.cmcTicket = $scope.stage.context.cmcTicket;
      $scope.scope = $scope.stage.context.scope;
    };

    this.propertyScopeForDisplay = () => {
      let temp = _.omit($scope.scope, ['appIdList']);
      return Object.assign(temp, {'app': _.head($scope.scope.appIdList) });
    };

    this.getErrorMessage = () => {
      return $scope.stage.context.exception.details.error;
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
