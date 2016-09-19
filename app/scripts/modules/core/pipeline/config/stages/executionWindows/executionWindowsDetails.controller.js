'use strict';

import { DAYS_OF_WEEK } from './daysOfWeek';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindows.details.controller', [
  require('angular-ui-router'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  require('../../../../delivery/service/execution.service'),
  require('../../../../confirmationModal/confirmationModal.service'),
])
  .controller('ExecutionWindowsDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, executionService, confirmationModalService) {

    $scope.configSections = ['windowConfig', 'taskStatus'];

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

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
      $scope.dayText = getDayText();
    }

    initialize();

    this.finishWaiting = () => {
      let stage = $scope.stage;
      let matcher = (execution) => {
        let [match] = execution.stages.filter((test) => test.id === stage.id);
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

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
