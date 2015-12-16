'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.delete', [
  require('../../services/services.module.js'),
])
  .controller('DeletePipelineModalCtrl', function($scope, $modalInstance, $log,
                                                  dirtyPipelineTracker, pipelineConfigService,
                                                  application, pipeline, $state) {

    this.cancel = $modalInstance.dismiss;

    $scope.viewState = {};

    $scope.pipeline = pipeline;

    this.deletePipeline = function() {
      return pipelineConfigService.deletePipeline(application.name, pipeline, pipeline.name).then(
        function() {
          application.pipelineConfigs.splice(application.pipelineConfigs.indexOf(pipeline), 1);
          application.pipelineConfigs.forEach(function(pipeline, index) {
            if (pipeline.index !== index) {
              pipeline.index = index;
              pipelineConfigService.savePipeline(pipeline);
            }
          });
          dirtyPipelineTracker.remove(pipeline.name);
          application.reloadPipelineConfigs();
          $state.go('^.executions', null, {location: 'replace'});
        },
        function(response) {
          $log.warn(response);
          $scope.viewState.saveError = true;
          $scope.viewState.errorMessage = response.message || 'No message provided';
        }
      );
    };

  });
