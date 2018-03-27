import { module } from 'angular';

import { IStage } from 'core/domain';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { ExecutionDetailsTasks } from '../core';

import { WaitExecutionDetails } from './WaitExecutionDetails';
import { WaitExecutionLabel } from './WaitExecutionLabel';

export const WAIT_STAGE = 'spinnaker.core.pipeline.stage.waitStage';

export const DEFAULT_SKIP_WAIT_TEXT = 'The pipeline will proceed immediately, marking this stage completed.';

module(WAIT_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      label: 'Wait',
      description: 'Waits a specified period of time',
      key: 'wait',
      templateUrl: require('./waitStage.html'),
      executionDetailsSections: [ WaitExecutionDetails, ExecutionDetailsTasks ],
      executionLabelComponent: WaitExecutionLabel,
      useCustomTooltip: true,
      strategy: true,
      controller: 'WaitStageCtrl as ctrl',
      validators: [
        { type: 'requiredField', fieldName: 'waitTime' },
      ],
    });
  }).controller('WaitStageCtrl', function (stage: IStage) {
    stage.waitTime = stage.waitTime || 30;
    this.enableCustomSkipWaitText = !!stage.skipWaitText;
    this.defaultSkipWaitText = DEFAULT_SKIP_WAIT_TEXT;
  });
