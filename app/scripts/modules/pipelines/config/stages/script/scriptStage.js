'use strict';

let angular = require('angular');

require('./scriptStage.html');
require('./scriptExecutionDetails.html');

module.exports = angular.module('spinnaker.pipelines.stage.scriptStage', [
  require('./script.service.js')
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
  })
  .name;
