'use strict';

let angular = require('angular');

import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';

module.exports = angular.module('spinnaker.core.pipeline.config.actions.delete', [
  PIPELINE_CONFIG_SERVICE,
  require('angular-ui-router')
])
  .controller('DeletePipelineModalCtrl', function($scope, $uibModalInstance, $log,
                                                  pipelineConfigService,
                                                  application, pipeline, $state) {

    this.cancel = $uibModalInstance.dismiss;

    $scope.viewState = {};

    $scope.pipeline = pipeline;

    this.deletePipeline = function() {
      return pipelineConfigService.deletePipeline(application.name, pipeline, pipeline.name).then(
        function() {
          application.pipelineConfigs.data.splice(application.pipelineConfigs.data.indexOf(pipeline), 1);
          application.pipelineConfigs.data.forEach(function(pipeline, index) {
            if (pipeline.index !== index) {
              pipeline.index = index;
              pipelineConfigService.savePipeline(pipeline);
            }
          });
          application.pipelineConfigs.refresh();
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
