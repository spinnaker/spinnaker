'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.script')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Script',
      description: 'Runs a script',
      key: 'script',
      controller: 'ScriptStageCtrl',
      controllerAs: 'scriptStageCtrl',
      templateUrl: require('./scriptStage.html'),
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/script/scriptExecutionDetails.html',
    });
  })
  .controller('ScriptStageCtrl', function($scope, stage, scriptService, $q, authenticationService) {
    $scope.stage = stage;

    if (!$scope.stage.user) {
      $scope.stage.user = authenticationService.getAuthenticatedUser().name;
    }

    $scope.viewState = {
      loading: true
    };

    $q.all({
      credentials: scriptService.getCredentials()
    }).then(function(results) {
      $scope.credentials = results.credentials;
      $scope.viewState.loading = false;
    });
  }).name;
