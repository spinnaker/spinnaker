'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.scriptStage', [
])
    .config(function(pipelineConfigProvider) {
      pipelineConfigProvider.registerStage({
        label: 'Script',
        description: 'Runs a script',
        key: 'script',
        controller: 'ScriptStageCtrl',
        controllerAs: 'scriptStageCtrl',
        templateUrl: require('./scriptStage.html'),
        executionDetailsUrl: require('./scriptExecutionDetails.html'),
      });
    })
    .controller('ScriptStageCtrl', function($scope, stage, authenticationService) {
      $scope.stage = stage;

      if (!$scope.stage.user) {
        $scope.stage.user = authenticationService.getAuthenticatedUser().name;
      }

      $scope.viewState = {
        loading: false
      };
    })
    .name;
