'use strict';

const angular = require('angular');

import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

module.exports = angular
  .module('spinnaker.core.pipeline.config.actions.lock', [])
  .controller('LockPipelineModalCtrl', ['$uibModalInstance', 'pipeline', function($uibModalInstance, pipeline) {
    this.viewState = {};
    this.pipelineName = pipeline.name;
    this.cancel = $uibModalInstance.dismiss;

    this.command = {
      allowUnlockUi: true,
    };

    this.lockPipeline = () => {
      pipeline.locked = {
        ui: true,
        allowUnlockUi: this.command.allowUnlockUi,
        description: this.command.description,
      };
      return PipelineConfigService.savePipeline(pipeline).then(
        () => $uibModalInstance.close(),
        response => {
          this.viewState.saveError = true;
          this.viewState.errorMessage = response.message || 'No message provided';
        },
      );
    };
  }]);
