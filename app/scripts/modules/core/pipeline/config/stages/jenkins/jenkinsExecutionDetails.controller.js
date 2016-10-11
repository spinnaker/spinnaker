'use strict';

import detailsSectionModule from 'core/delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.jenkins.executionDetails.controller', [
  require('angular-ui-router'),
  detailsSectionModule,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('JenkinsExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['jenkinsConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
      getFailureMessage();
    };

    function getFailureMessage() {
      var failureMessage = $scope.stage.failureMessage,
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

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
