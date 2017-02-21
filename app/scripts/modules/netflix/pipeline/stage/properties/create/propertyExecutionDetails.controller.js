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
      $scope.properties = getPropertiesForAction($scope.stage.context);
      $scope.originalProperties = extractProperties($scope.stage.context.originalProperties);
      $scope.rolledBack = $scope.stage.context.rollbackProperties;
      $scope.notificationEmail = $scope.stage.context.email;
      $scope.cmcTicket = $scope.stage.context.cmcTicket;
      $scope.scope = $scope.stage.context.scope;
      $scope.propertyAction = $scope.stage.context.propertyAction;
      $scope.pipelineStatus = $scope.execution.status;
    };

    this.propertyScopeForDisplay = () => {
      let temp = _.omit($scope.scope, ['appIdList', 'instanceCounts']);
      return Object.assign(temp, {'app': _.head($scope.scope.appIdList) });
    };

    this.getErrorMessage = () => {
      return $scope.stage.context.exception.details.error;
    };

    this.isExecutionTerminalOrCanceled = () => {
      return $scope.pipelineStatus === 'TERMINAL' || $scope.pipelineStatus === 'CANCELED';
    };

    this.wasCreateAction = () => {
      return $scope.propertyAction === 'CREATE';
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

    let getPropertiesForAction = (stageContext) => {
      if (stageContext.propertyAction === 'DELETE' && stageContext.originalProperties) {
        return extractProperties(stageContext.originalProperties);
      }
      return stageContext.persistedProperties;
    };

    let extractProperties = (propertyList) => {
      if (propertyList.length) return propertyList.map( prop => prop.property );
    };


  });
