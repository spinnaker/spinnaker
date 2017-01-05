'use strict';

import {DAYS_OF_WEEK} from './daysOfWeek';
import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {EXECUTION_DETAILS_SECTION_SERVICE} from 'core/delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindows.details.controller', [
  require('angular-ui-router'),
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
  require('core/delivery/service/execution.service'),
  CONFIRMATION_MODAL_SERVICE,
])
  .controller('ExecutionWindowsDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, executionService, confirmationModalService) {

    $scope.configSections = ['windowConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.dayText = getDayText();
    };

    // yes, this is ugly - when we replace the execution window w/ an ng2 component, this will go away.  i promise
    function replaceDays(days) {
      const daySet = new Set(days);
      return DAYS_OF_WEEK.filter(day => daySet.has(day.ordinal)).map(day => day.label);
    }

    // ditto
    function getDayText() {
      let dayText = 'Everyday';
      const days = $scope.stage.context.restrictedExecutionWindow.days;
      if (days && (days.length > 0)) {
        const daysAsText = replaceDays(days);
        dayText = daysAsText.join(', ');
      }

      return dayText;
    }

    this.finishWaiting = () => {
      let stage = $scope.stage;
      let matcher = (execution) => {
        let match = execution.stages.find((test) => test.id === stage.id);
        return match.status !== 'RUNNING';
      };

      let data = {skipRemainingWait: true};
      confirmationModalService.confirm({
        header: 'Really skip execution window?',
        buttonText: 'Skip',
        body: '<p>The pipeline will proceed immediately, continuing to the next step in the stage.</p>',
        submitMethod: () => {
          return executionService.patchExecution($scope.execution.id, $scope.stage.id, data)
            .then(() => executionService.waitUntilExecutionMatches($scope.execution.id, matcher))
            .then(() => {
              executionService.getExecution($scope.execution.id).then(execution => {
                if (!$scope.$$destroyed) {
                  executionService.updateExecution($scope.application, execution);
                }
              });
            });
        }
      });
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
