'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.runJob.titus.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../../../core/delivery/details/executionDetailsSection.service.js'),
  require('../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('titusRunJobExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, accountService) {

    $scope.configSections = ['runJobConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();
    $scope.$on('$stateChangeSuccess', initialize, true);

    $scope.job = $scope.stage.context['deploy.jobs'][$scope.stage.context.cluster.region][0];

    accountService.getAccountDetails($scope.stage.context.credentials).then((details) => {
      $scope.apiEndpoint = _.where(details.regions, {name: $scope.stage.context.cluster.region})[0].endpoint;
      $scope.titusUiEndpoint = $scope.apiEndpoint.replace('titusapi', 'titus-ui').replace('http', 'https').replace('7101', '7001');
      if ($scope.stage.context.jobStatus.completionDetails) {
        $scope.task = $scope.stage.context.jobStatus.completionDetails.taskId;
      }
    });
  });
