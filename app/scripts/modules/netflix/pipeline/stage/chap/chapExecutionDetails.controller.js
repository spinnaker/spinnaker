'use strict';

import {NetflixSettings} from 'netflix/netflix.settings';

const angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.chap.executionDetails.controller', [
  require('angular-ui-router').default,
])
  .controller('ChapExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['testRunDetails', 'taskStatus'];

    const initialized = () => {
      const run = $scope.stage.context.run;
      $scope.detailsSection = $stateParams.details;

      $scope.canaryReports = Object.keys(run.properties.analysisIds).map(key => ({
        key,
        url: [
          NetflixSettings.chap.canaryReportBaseUrl,
          run.testCase.properties.account,
          run.testCase.properties.region,
          key,
          run.properties.analysisIds[key],
        ].join('/')
      }));

      $scope.testCaseUrl = [
        NetflixSettings.chap.chapBaseUrl,
        'testcases',
        run.testCase.id,
      ].join('/');

      $scope.runUrl = [
        NetflixSettings.chap.chapBaseUrl,
        'runs',
        run.id,
      ].join('/');

      $scope.fitScenarioUrl = [
        NetflixSettings.chap.fitBaseUrl,
        'scenarios',
        run.testCase.properties.scenario,
      ].join('/');
    };

    const initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
