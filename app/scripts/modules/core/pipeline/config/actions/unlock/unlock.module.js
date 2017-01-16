'use strict';

let angular = require('angular');

import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';

module.exports = angular.module('spinnaker.core.pipeline.config.actions.unlock', [
  PIPELINE_CONFIG_SERVICE,
])
  .controller('unlockPipelineModalCtrl', function($uibModalInstance, pipelineConfigService, pipeline) {
    this.viewState = {};
    this.pipelineName = pipeline.name;
    this.cancel = $uibModalInstance.dismiss;

    this.unlockPipeline = () => {
      delete pipeline.locked;
      return pipelineConfigService.savePipeline(pipeline).then(
        () => $uibModalInstance.close(),
        (response) => {
          this.viewState.saveError = true;
          this.viewState.errorMessage = response.message || 'No message provided';
        }
      );
    };

  });
