'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.manualJudgmentStage', [])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Manual Judgment',
      description: 'Waits for user approval before continuing',
      key: 'manualJudgment',
      restartable: true,
      controller: 'ManualJudgmentStageCtrl',
      controllerAs: 'manualJudgmentStageCtrl',
      templateUrl: require('./manualJudgmentStage.html'),
      executionDetailsUrl: require('./manualJudgmentExecutionDetails.html'),
      strategy: true,
    });
  })
  .controller('ManualJudgmentStageCtrl', function($scope, $uibModal) {

    $scope.stage.notifications = $scope.stage.notifications || [];
    $scope.stage.failPipeline = ($scope.stage.failPipeline === undefined ? true : $scope.stage.failPipeline);

    this.addNotification = function() {
      $uibModal.open({
        templateUrl: require('./modal/editNotification.html'),
        controller: 'ManualJudgmentEditNotificationController',
        controllerAs: 'editNotification',
        resolve: {
          notification: function () {
            return {};
          }
        }
      }).result.then(function(notification) {
          $scope.stage.notifications.push(notification);
      });
    };

    this.removeNotification = function (idx) {
      $scope.stage.notifications.splice(idx, 1);
    };

  });
