'use strict';

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

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    this.finishWaiting = () => {
      let stage = $scope.stage;
      let matcher = (execution) => {
        let [match] = execution.stages.filter((test) => test.id === stage.id);
        return match.status !== 'RUNNING';
      };

      let data = { skipRemainingWait: true };
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
