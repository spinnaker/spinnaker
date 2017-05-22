'use strict';

import { get, has, filter } from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.titus.pipeline.stage.runJob.executionDetails.controller', [
  require('angular-ui-router').default,
])
  .controller('titusRunJobExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, accountService) {

    $scope.configSections = ['runJobConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;

      let [job] = get($scope.stage.context['deploy.jobs'], $scope.stage.context.cluster.region, []);
      $scope.job = job;

      if (has($scope.stage.context, 'jobStatus.completionDetails')) {
        $scope.task = $scope.stage.context.jobStatus.completionDetails.taskId;
      }

      accountService.getAccountDetails($scope.stage.context.credentials).then((details) => {
        $scope.apiEndpoint = filter(details.regions, {name: $scope.stage.context.cluster.region})[0].endpoint;
        $scope.titusUiEndpoint = $scope.apiEndpoint.replace('titusapi', 'titus-ui').replace('http', 'https').replace('7101', '7001');
      });
    };

    let initialize = () => {
      executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);
    };

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
