'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.property.details.controller', [
  require('angular-ui-router'),
  require('../../../../../core/delivery/details/executionDetailsSection.service.js'),
  require('../../../../../core/delivery/details/executionDetailsSectionNav.directive.js')
])
  .controller('PropertyExecutionDetailsCtrl', function ($scope, $stateParams, $timeout, executionDetailsSectionService) {

    let vm = this;
    $scope.configSections = ['propertiesConfig', 'taskStatus'];

    function initialize() {

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update any scope values based on the stage
      $timeout(function() {
        executionDetailsSectionService.synchronizeSection($scope.configSections);
        $scope.detailsSection = $stateParams.details;

        $scope.properties = $scope.stage.context.persistedProperties;
        $scope.notificationEmail = $scope.stage.context.email;
        $scope.cmcTicket = $scope.stage.context.cmcTicket;
        $scope.scope = $scope.stage.context.scope;
      });
    }

    initialize();


    vm.propertyScopeForDisplay = () => {
      let temp = _.omit($scope.scope, ['appIdList']);
      return Object.assign(temp, {'app': _.head($scope.scope.appIdList) });
    };

    vm.getErrorMessage = () => {
      return $scope.stage.context.exception.details.error;
    };

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
