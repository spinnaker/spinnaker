'use strict';

import _ from 'lodash';
import {EXECUTION_DETAILS_SECTION_SERVICE} from 'core/delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.runJob.titus.executionDetails.controller', [
  require('angular-ui-router').default,
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('titusRunJobExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, accountService) {

    $scope.configSections = ['runJobConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;

      let [job] = _.get($scope.stage.context['deploy.jobs'], $scope.stage.context.cluster.region, []);
      $scope.job = job;

      if (_.has($scope.stage.context, 'jobStatus.completionDetails')) {
        $scope.task = $scope.stage.context.jobStatus.completionDetails.taskId;
      }

      accountService.getAccountDetails($scope.stage.context.credentials).then((details) => {
        $scope.apiEndpoint = _.filter(details.regions, {name: $scope.stage.context.cluster.region})[0].endpoint;
        $scope.titusUiEndpoint = $scope.apiEndpoint.replace('titusapi', 'titus-ui').replace('http', 'https').replace('7101', '7001');
      });
    };

    let initialize = () => {
      executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);
    };

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
