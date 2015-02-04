'use strict';

angular.module('deckApp.pipelines.rename')
  .controller('RenamePipelineModalCtrl', function($scope, application, pipeline, _, pipelineConfigService, $modalInstance, $log) {

    this.cancel = $modalInstance.dismiss;

    var currentName = pipeline.name;

    $scope.viewState = {};

    $scope.pipeline = pipeline;
    $scope.existingNames = _.without(_.pluck(application.pipelines, 'name'), currentName);
    $scope.command = {
      newName: currentName
    };

    this.renamePipeline = function() {
      pipeline.name = $scope.newName;
      return pipelineConfigService.renamePipeline(application.name, currentName, $scope.command.newName).then(
        function() {
          $scope.pipeline.name = $scope.command.newName;
          $modalInstance.close();
        },
        function(response) {
          $log.warn(response);
          $scope.viewState.saveError = true;
          $scope.viewState.errorMessage = response.message || 'No message provided';
        }
      );
    };

  });
