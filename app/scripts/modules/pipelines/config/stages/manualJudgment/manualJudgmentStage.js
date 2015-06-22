'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.manualJudgment')
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Manual Judgment',
      description: 'Waits for user approval before continuing',
      key: 'manualJudgment',
      controller: 'ManualJudgmentStageCtrl',
      controllerAs: 'manualJudgmentStageCtrl',
      template: require('./manualJudgmentStage.html'),
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/manualJudgment/manualJudgmentExecutionDetails.html',
      executionBarColorProvider: function (stageSummary) {
        if (stageSummary.status === 'RUNNING') {
          return '#F0AD4E';
        }

        return undefined;
      }
    });
  })
  .controller('ManualJudgmentStageCtrl', function($scope, $modal) {

    $scope.stage.notifications = $scope.stage.notifications || [];

    this.addNotification = function() {
      $modal.open({
        template: require('./modal/editNotification.html'),
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
