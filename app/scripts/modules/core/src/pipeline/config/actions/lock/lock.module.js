'use strict';

const angular = require('angular');

import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';

module.exports = angular.module('spinnaker.core.pipeline.config.actions.lock', [
  PIPELINE_CONFIG_SERVICE,
])
  .controller('LockPipelineModalCtrl', function($uibModalInstance, pipelineConfigService, pipeline) {
    this.viewState = {};
    this.pipelineName = pipeline.name;
    this.cancel = $uibModalInstance.dismiss;

    this.command = {
      allowUnlockUi: true
    };

    this.lockPipeline = () => {
      pipeline.locked = {
        ui: true,
        allowUnlockUi: this.command.allowUnlockUi,
        description: this.command.description,
      };
      return pipelineConfigService.savePipeline(pipeline).then(
        () => $uibModalInstance.close(),
        (response) => {
          this.viewState.saveError = true;
          this.viewState.errorMessage = response.message || 'No message provided';
        }
      );
    };

  });
