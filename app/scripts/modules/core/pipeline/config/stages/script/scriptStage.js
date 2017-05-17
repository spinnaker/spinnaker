'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.scriptStage', [
])
    .config(function(pipelineConfigProvider) {
      pipelineConfigProvider.registerStage({
        label: 'Script',
        description: 'Runs a script',
        key: 'script',
        restartable: true,
        controller: 'ScriptStageCtrl',
        controllerAs: 'scriptStageCtrl',
        templateUrl: require('./scriptStage.html'),
        executionDetailsUrl: require('./scriptExecutionDetails.html'),
        executionConfigSections: ['scriptConfig', 'taskStatus'],
        strategy: true,
      });
    })
    .controller('ScriptStageCtrl', function($scope, stage, authenticationService) {
      $scope.stage = stage;
      $scope.stage.failPipeline = ($scope.stage.failPipeline === undefined ? true : $scope.stage.failPipeline);
      $scope.stage.waitForCompletion = ($scope.stage.waitForCompletion === undefined ? true : $scope.stage.waitForCompletion);

      if (!$scope.stage.user) {
        $scope.stage.user = authenticationService.getAuthenticatedUser().name;
      }

      $scope.viewState = {
        loading: false
      };
    });
