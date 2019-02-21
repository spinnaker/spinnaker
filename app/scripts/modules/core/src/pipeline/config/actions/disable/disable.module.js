'use strict';

const angular = require('angular');

import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

module.exports = angular
  .module('spinnaker.core.pipeline.config.actions.disable', [])
  .controller('DisablePipelineModalCtrl', [
    '$uibModalInstance',
    'pipeline',
    function($uibModalInstance, pipeline) {
      this.viewState = {};

      this.pipelineName = pipeline.name;

      this.cancel = $uibModalInstance.dismiss;

      this.disablePipeline = () => {
        pipeline.disabled = true;
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
