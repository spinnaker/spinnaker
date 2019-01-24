'use strict';

const angular = require('angular');

import { Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.canary.summary.controller', [
    require('@uirouter/angularjs').default,
    require('./actions/generateScore.controller').name,
    require('./actions/endCanary.controller').name,
  ])
  .controller('CanaryExecutionSummaryCtrl', function($scope, $http, $uibModal) {
    this.generateCanaryScore = function() {
      $uibModal.open({
        templateUrl: require('./actions/generateScore.modal.html'),
        controller: 'GenerateScoreCtrl as ctrl',
        resolve: {
          canaryId: function() {
            return $scope.stageSummary.masterStage.context.canary.id;
          },
        },
      });
    };

    this.endCanary = function() {
      $uibModal.open({
        templateUrl: require('./actions/endCanary.modal.html'),
        controller: 'EndCanaryCtrl as ctrl',
        resolve: {
          canaryId: function() {
            return $scope.stageSummary.masterStage.context.canary.id;
          },
        },
      });
    };

    this.isRestartable = function(stage) {
      var stageConfig = Registry.pipeline.getStageConfig(stage);
      if (!stageConfig || stage.isRestarting === true) {
        return false;
      }

      return stageConfig.restartable || false;
    };
  });
