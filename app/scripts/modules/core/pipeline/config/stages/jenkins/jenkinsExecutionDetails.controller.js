'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.jenkins.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('JenkinsExecutionDetailsCtrl', function ($scope, $stateParams, $timeout, executionDetailsSectionService) {

    $scope.configSections = ['jenkinsConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
      $timeout(getFailureMessage);
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
