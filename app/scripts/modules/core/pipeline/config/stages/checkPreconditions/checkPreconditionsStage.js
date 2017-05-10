'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.checkPreconditionsStage', [])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Check Preconditions',
      description: 'Checks for preconditions before continuing',
      key: 'checkPreconditions',
      restartable: true,
      controller: 'CheckPreconditionsStageCtrl',
      controllerAs: 'checkPreconditionsStageCtrl',
      templateUrl: require('./checkPreconditionsStage.html'),
      executionDetailsUrl: require('./checkPreconditionsExecutionDetails.html'),
      executionConfigSections: ['checkPreconditions', 'taskStatus'],
      strategy: true,
    });
  })
  .controller('CheckPreconditionsStageCtrl', function($scope) {
    $scope.stage.preconditions = $scope.stage.preconditions || [];
  });
