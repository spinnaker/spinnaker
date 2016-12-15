'use strict';

import {EXECUTION_DETAILS_SECTION_SERVICE} from 'core/delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.wait.executionDetails.controller', [
  require('angular-ui-router'),
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
  require('core/delivery/service/execution.service'),
  require('core/confirmationModal/confirmationModal.service'),
])
  .controller('WaitExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, executionService, confirmationModalService) {

    $scope.configSections = ['waitConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
    };

    this.getRemainingWait = () => {
      return $scope.stage.context.waitTime * 1000 - $scope.stage.runningTimeInMs;
    };

    this.finishWaiting = () => {
      let stage = $scope.stage;
      let matcher = (execution) => {
        let match = execution.stages.find((test) => test.id === stage.id);
        return match.status !== 'RUNNING';
      };

      let data = { skipRemainingWait: true };
      confirmationModalService.confirm({
        header: 'Really skip wait?',
        buttonText: 'Skip',
        body: '<p>The pipeline will proceed immediately, marking this stage completed.</p>',
        submitMethod: () => {
          return executionService.patchExecution($scope.execution.id, $scope.stage.id, data)
            .then(() => executionService.waitUntilExecutionMatches($scope.execution.id, matcher));
        }
      });
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
