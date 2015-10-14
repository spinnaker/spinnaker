'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executionBar.controller', [
  require('../../core/pipeline/config/pipelineConfigProvider.js'),
  require('angular-ui-router'),
])
  .controller('executionBar', function($scope, $filter, $stateParams, pipelineConfig, $state) {
    function updateDetails(params) {
      var param = params.stage ? parseInt(params.stage) : 0;
      $scope.execution.stageSummaries.forEach(function(stage) {
        stage.showingDetails = $scope.execution.id === params.executionId && $scope.execution.stageSummaries.indexOf(stage) === param;
      });
    }

    this.toggleDetails = function(executionId, stageIndex) {
      var stageSummary = $scope.execution.stageSummaries[stageIndex],
          masterIndex = stageSummary.masterStageIndex;
      if ($state.includes('**.execution', {executionId: executionId, stage: stageIndex})) {
        $state.go('^');
      } else {
        if ($state.includes('**.execution')) {
          $state.go('^.execution', {executionId: executionId, stage: stageIndex, step: masterIndex});
        } else {
          $state.go('.execution', {executionId: executionId, stage: stageIndex, step: masterIndex});
        }
      }
    };

    // initialization needed for deep links
    updateDetails($stateParams);

    $scope.$on('$stateChangeSuccess', function(event, toState, toParams) {
      updateDetails(toParams);
    });

  }).name;
