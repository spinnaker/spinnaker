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
    });
  })
  .controller('CheckPreconditionsStageCtrl', function($scope, $modal) {

    $scope.stage.preconditions = $scope.stage.preconditions || [];

    this.addPrecondition = function() {
      $modal.open({
        templateUrl: require('./modal/editPrecondition.html'),
        controller: 'CheckPreconditionsEditPreconditionController',
        controllerAs: 'editPrecondition',
        resolve: {
          precondition: function () {
            return {};
          }
        }
      }).result.then(function(precondition) {
          $scope.stage.preconditions.push(precondition);
      });
    };

    this.removePrecondition = function (idx) {
      $scope.stage.preconditions.splice(idx, 1);
    };

  })
  .name;
