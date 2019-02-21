'use strict';

const angular = require('angular');

import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

module.exports = angular
  .module('spinnaker.core.pipeline.config.actions.unlock', [])
  .controller('unlockPipelineModalCtrl', [
    '$uibModalInstance',
    'pipeline',
    function($uibModalInstance, pipeline) {
      this.viewState = {};
      this.pipelineName = pipeline.name;
      this.cancel = $uibModalInstance.dismiss;

      this.unlockPipeline = () => {
        delete pipeline.locked;
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
