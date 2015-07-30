'use strict';

angular.module('spinnaker.pipelines.stage.script')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Script',
      description: 'Runs a script',
      key: 'script',
      controller: 'ScriptStageCtrl',
      controllerAs: 'scriptStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/script/scriptStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/script/scriptExecutionDetails.html',
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

  });
