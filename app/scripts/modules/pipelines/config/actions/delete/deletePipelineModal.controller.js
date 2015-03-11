'use strict';

angular.module('deckApp.pipelines.delete')
  .controller('DeletePipelineModalCtrl', function($scope, application, pipeline, pipelineConfigService, $modalInstance, $log) {

    this.cancel = $modalInstance.dismiss;

    $scope.viewState = {};

    $scope.pipeline = pipeline;

    this.deletePipeline = function() {
      return pipelineConfigService.deletePipeline(application.name, pipeline.name).then(
        function() {
          application.pipelines.splice(application.pipelines.indexOf(pipeline), 1);
          application.pipelines.forEach(function(pipeline, index) {
            if (pipeline.index !== index) {
              pipeline.index = index;
              pipelineConfigService.savePipeline(pipeline);
            }
          });

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
