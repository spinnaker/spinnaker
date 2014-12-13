'use strict';

angular.module('deckApp.pipelines.create')
  .controller('CreatePipelineModalCtrl', function($scope, application, _, pipelineConfigService, $modalInstance, $log) {

    var noTemplate = {name: 'None'};

    $scope.viewState = {};

    application.pipelines = application.pipelines || [];

    $scope.templates = [noTemplate].concat(application.pipelines);
    $scope.existingNames = _.pluck($scope.templates, 'name');

    $scope.command = {
      template: noTemplate
    };

    this.cancel = $modalInstance.dismiss;

    this.createPipeline = function() {
      var pipeline = angular.copy($scope.command.template);
      pipeline.name = $scope.command.name;
      pipeline.application = application.name;
      pipeline.stages = [];
      pipeline.triggers = [];

      return pipelineConfigService.savePipeline(pipeline).then(
        function() {
          application.pipelines.push(pipeline);
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
