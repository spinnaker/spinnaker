'use strict';

angular.module('deckApp.pipelines.rename')
  .controller('EditPipelineJsonModalCtrl', function($scope, pipeline, _, $modalInstance) {

    this.cancel = $modalInstance.dismiss;

    function removeImmutableFields(obj) {
      if (obj.hasOwnProperty('name')) {
        delete obj.name;
      }
      if (obj.hasOwnProperty('application')) {
        delete obj.application;
      }
      if (obj.hasOwnProperty('appConfig')) {
        delete obj.appConfig;
      }
      if (obj.hasOwnProperty('index')) {
        delete obj.index;
      }
    }

    this.initialize = function() {
      var toCopy = pipeline.hasOwnProperty('plain') ? pipeline.plain() : pipeline;
      var pipelineCopy = _.cloneDeep(toCopy, function (value) {
        if (value && value.$$hashKey) {
          delete value.$$hashKey;
        }
      });
      removeImmutableFields(pipelineCopy);

      $scope.command = {
        pipelineJSON: JSON.stringify(pipelineCopy, null, 2)
      };
    };

    this.updatePipeline = function() {
      try {
        var parsed = JSON.parse($scope.command.pipelineJSON);

        removeImmutableFields(parsed);
        angular.extend(pipeline, parsed);

        $modalInstance.close();
      } catch (e) {
        $scope.command.invalid = true;
        $scope.command.errorMessage = e.message;
      }
    };

    this.initialize();

  });
