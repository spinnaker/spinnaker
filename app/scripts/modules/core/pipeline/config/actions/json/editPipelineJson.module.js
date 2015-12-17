'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.editJson', [
  require('../../../../utils/lodash.js'),
])
  .controller('EditPipelineJsonModalCtrl', function($scope, pipeline, _, $modalInstance) {

    this.cancel = $modalInstance.dismiss;

    function removeImmutableFields(obj) {
      delete obj.name;
      delete obj.application;
      delete obj.index;
      delete obj.id;
      delete obj.stageCounter;
    }

    function updateStageCounter() {
      if (pipeline.parallel) {
        let stageIds = pipeline.stages.map((stage) => Number(stage.refId));
        stageIds.forEach((stageId) => pipeline.stageCounter = Math.max(pipeline.stageCounter, stageId));
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

      $scope.isStrategy =  pipelineCopy.strategy || false;

      $scope.command = {
        pipelineJSON: JSON.stringify(pipelineCopy, null, 2)
      };
    };

    this.updatePipeline = function() {
      try {
        var parsed = JSON.parse($scope.command.pipelineJSON);
        parsed.appConfig = parsed.appConfig || {};

        removeImmutableFields(parsed);
        angular.extend(pipeline, parsed);
        updateStageCounter();

        $modalInstance.close();
      } catch (e) {
        $scope.command.invalid = true;
        $scope.command.errorMessage = e.message;
      }
    };

    this.initialize();

  });

