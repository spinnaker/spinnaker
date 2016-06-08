'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.rename', [
  require('../../../../utils/lodash.js'),
  require('../../services/dirtyPipelineTracker.service.js'),
  require('../../services/pipelineConfigService.js'),
])
  .controller('RenamePipelineModalCtrl', function($scope, application, pipeline, _, $uibModalInstance, $log,
                                                  dirtyPipelineTracker, pipelineConfigService) {

    this.cancel = $uibModalInstance.dismiss;

    var currentName = pipeline.name;

    $scope.viewState = {};

    $scope.pipeline = pipeline;
    $scope.existingNames = _.without(_.pluck(application.pipelineConfigs.data, 'name'), currentName);
    $scope.command = {
      newName: currentName
    };

    this.renamePipeline = function() {
      pipeline.name = $scope.newName;
      $scope.viewState.saving = true;
      return pipelineConfigService.renamePipeline(application.name, pipeline, currentName, $scope.command.newName).then(
        function() {
          $scope.pipeline.name = $scope.command.newName;
          if (dirtyPipelineTracker.list().indexOf(currentName) > -1) {
            dirtyPipelineTracker.remove(currentName);
            dirtyPipelineTracker.add(pipeline.name);
          }
          application.pipelineConfigs.refresh();
          $uibModalInstance.close();
        },
        function(response) {
          $log.warn(response);
          $scope.viewState.saving = false;
          $scope.viewState.saveError = true;
          $scope.viewState.errorMessage = response.message || 'No message provided';
        }
      );
    };

  });
