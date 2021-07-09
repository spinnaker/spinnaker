import UIROUTER_ANGULARJS from '@uirouter/angularjs';
('use strict');

import { module } from 'angular';

export const CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSEXECUTIONDETAILS_CONTROLLER =
  'spinnaker.core.pipeline.stage.jenkins.executionDetails.controller';
export const name = CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSEXECUTIONDETAILS_CONTROLLER, [UIROUTER_ANGULARJS]).controller(
  'JenkinsExecutionDetailsCtrl',
  [
    '$scope',
    '$stateParams',
    'executionDetailsSectionService',
    function ($scope, $stateParams, executionDetailsSectionService) {
      $scope.configSections = ['jenkinsConfig', 'taskStatus', 'artifactStatus'];

      const initialized = () => {
        $scope.detailsSection = $stateParams.details;
        getFailureMessage();
      };

      function getFailureMessage() {
        let failureMessage = $scope.stage.failureMessage;
        const context = $scope.stage.context || {};
        const buildInfo = context.buildInfo || {};
        const testResults =
          buildInfo && buildInfo.testResults && buildInfo.testResults.length ? buildInfo.testResults : [];
        const failingTests = testResults.filter(function (results) {
          return results.failCount > 0;
        });
        const failingTestCount = failingTests.reduce(function (acc, results) {
          return acc + results.failCount;
        }, 0);

        if (buildInfo.result === 'FAILURE') {
          failureMessage = 'Build failed.';
        }

        if (failingTestCount) {
          failureMessage = failingTestCount + ' test(s) failed.';
        }

        $scope.failureMessage = failureMessage;
      }

      const initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

      initialize();

      $scope.$on('$stateChangeSuccess', initialize);
    },
  ],
);
