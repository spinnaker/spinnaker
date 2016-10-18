'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.summary.controller', [
  require('angular-ui-router'),
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
  require('./actions/generateScore.controller.js'),
  require('./actions/endCanary.controller.js'),
  require('../../../../core/pipeline/config/pipelineConfigProvider.js')
])
  .controller('CanaryExecutionSummaryCtrl', function ($scope, $http, settings, $uibModal, pipelineConfig) {

    this.generateCanaryScore = function() {
      $uibModal.open({
        templateUrl: require('./actions/generateScore.modal.html'),
        controller: 'GenerateScoreCtrl as ctrl',
        resolve: {
          canaryId: function() { return $scope.stageSummary.masterStage.context.canary.id; },
        },
      });
    };

    this.endCanary = function() {
      $uibModal.open({
        templateUrl: require('./actions/endCanary.modal.html'),
        controller: 'EndCanaryCtrl as ctrl',
        resolve: {
          canaryId: function() { return $scope.stageSummary.masterStage.context.canary.id; },
        },
      });
    };

    this.isRestartable = function(stage) {
      var stageConfig = pipelineConfig.getStageConfig(stage);
      if (!stageConfig || stage.isRestarting === true) {
        return false;
      }

      return stageConfig.restartable || false;

    };

  });
