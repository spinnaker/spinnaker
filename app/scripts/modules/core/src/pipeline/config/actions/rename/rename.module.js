'use strict';

import _ from 'lodash';

const angular = require('angular');

import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

module.exports = angular
  .module('spinnaker.core.pipeline.config.actions.rename', [])
  .controller('RenamePipelineModalCtrl', [
    '$scope',
    'application',
    'pipeline',
    '$uibModalInstance',
    '$log',
    function($scope, application, pipeline, $uibModalInstance, $log) {
      this.cancel = $uibModalInstance.dismiss;

      var currentName = pipeline.name;

      $scope.viewState = {};

      $scope.pipeline = pipeline;
      $scope.existingNames = _.without(_.map(application.pipelineConfigs.data, 'name'), currentName);
      $scope.command = {
        newName: currentName,
      };

      this.renamePipeline = function() {
        pipeline.name = $scope.newName;
        $scope.viewState.saving = true;
        return PipelineConfigService.renamePipeline(
          application.name,
          pipeline,
          currentName,
          $scope.command.newName,
        ).then(
          function() {
            $scope.pipeline.name = $scope.command.newName;
            application.pipelineConfigs.refresh();
            $uibModalInstance.close();
          },
          function(response) {
            $log.warn(response);
            $scope.viewState.saving = false;
            $scope.viewState.saveError = true;
            $scope.viewState.errorMessage = response.message || 'No message provided';
          },
        );
      };
    },
  ]);
