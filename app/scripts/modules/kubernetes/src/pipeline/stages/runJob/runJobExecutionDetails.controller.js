'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.pipeline.stage.disableCluster.runJobExecutionDetails.controller', [
    require('@uirouter/angularjs').default,
  ])
  .controller('kubernetesRunJobExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, $uibModal) {

    $scope.configSections = ['runJobConfig', 'taskStatus'];

    // if the stage is pre-multi-containers
    if ($scope.stage.context.container) {
      $scope.stage.context.containers = [$scope.stage.context.container];
    }

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

    $scope.displayLogs = () => {
      $scope.logs = $scope.stage.context.jobStatus.logs || '';
      $scope.jobName = $scope.stage.context.jobStatus.name || '';
      return $uibModal.open({
        templateUrl: require('./runJobLogs.html'),
        scope: $scope,
        size: 'lg'
      });
    };

  });
