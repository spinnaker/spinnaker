'use strict';

angular.module('spinnaker.pipelines.stage.jenkins.executionDetails.controller', [
  'ui.router',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
])
  .controller('JenkinsExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['jenkinsConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
      getFailureMessage();
    }

    function getFailureMessage() {
      var failureMessage = 'No reason provided.',
          context = $scope.stage.context || {},
          buildInfo = context.buildInfo || {},
          testResults = buildInfo && buildInfo.testResults && buildInfo.testResults.length ?
            buildInfo.testResults : [],
          failingTests = testResults.filter(function(results) { return results.failCount > 0; }),
          failingTestCount = failingTests.reduce(function(acc, results) {
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

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
