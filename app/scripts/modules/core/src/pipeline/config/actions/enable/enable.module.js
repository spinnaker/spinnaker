'use strict';

const angular = require('angular');

import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

module.exports = angular
  .module('spinnaker.core.pipeline.config.actions.enable', [])
  .controller('EnablePipelineModalCtrl', [
    '$uibModalInstance',
    'pipeline',
    function($uibModalInstance, pipeline) {
      this.viewState = {};

      this.pipelineName = pipeline.name;

      this.cancel = $uibModalInstance.dismiss;

      this.enablePipeline = () => {
        pipeline.disabled = false;
        return PipelineConfigService.savePipeline(pipeline).then(
          () => $uibModalInstance.close(),
          response => {
            this.viewState.saveError = true;
            this.viewState.errorMessage = response.message || 'No message provided';
          },
        );
      };
    },
  ]);
