import { module } from 'angular';

import { IStage } from 'core/domain';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { ExecutionDetailsTasks } from '../core';

import { WaitExecutionDetails } from './WaitExecutionDetails';
import { WaitExecutionLabel } from './WaitExecutionLabel';

export const WAIT_STAGE = 'spinnaker.core.pipeline.stage.waitStage';

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
      controller: 'WaitStageCtrl',
      validators: [
        { type: 'requiredField', fieldName: 'waitTime' },
      ],
    });
  }).controller('WaitStageCtrl', (stage: IStage) => stage.waitTime = stage.waitTime || 30);
