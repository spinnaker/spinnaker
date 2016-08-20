'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.wait.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  require('../../../../delivery/service/execution.service'),
  require('../../../../confirmationModal/confirmationModal.service'),
])
  .controller('WaitExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, executionService, confirmationModalService) {

    $scope.configSections = ['waitConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    this.getRemainingWait = () => {
      return $scope.stage.context.waitTime * 1000 - $scope.stage.runningTimeInMs;
    };

    this.finishWaiting = () => {
      let stage = $scope.stage;
      let matcher = (execution) => {
        let [match] = execution.stages.filter((test) => test.id === stage.id);
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

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
