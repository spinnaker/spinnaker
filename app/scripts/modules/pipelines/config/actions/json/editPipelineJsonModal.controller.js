'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.actions.editJson.EditPipelineJsonModalCtrl', [
])
  .controller('EditPipelineJsonModalCtrl', function($scope, pipeline, _, $modalInstance) {

    this.cancel = $modalInstance.dismiss;

    function removeImmutableFields(obj) {
      delete obj.name;
      delete obj.application;
      delete obj.appConfig;
      delete obj.index;
      delete obj.id;
      delete obj.stageCounter;
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
