'use strict';

import { get, has, filter } from 'lodash';
import { SETTINGS } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.titus.pipeline.stage.runJob.executionDetails.controller', [require('@uirouter/angularjs').default])
  .controller('titusRunJobExecutionDetailsCtrl', function(
    $scope,
    $stateParams,
    executionDetailsSectionService,
    accountService,
  ) {
    $scope.configSections = ['runJobConfig', 'taskStatus'];
    $scope.gateUrl = SETTINGS.gateUrl;

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;

      let [job] = get($scope.stage.context['deploy.jobs'], $scope.stage.context.cluster.region, []);
      $scope.job = job;

      if (has($scope.stage.context, 'jobStatus.completionDetails')) {
        $scope.task = $scope.stage.context.jobStatus.completionDetails.taskId;
      }

      accountService.getAccountDetails($scope.stage.context.credentials).then(details => {
        $scope.titusUiEndpoint = filter(details.regions, { name: $scope.stage.context.cluster.region })[0].endpoint;
      });
    };

    let initialize = () => {
      executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);
    };

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  });
