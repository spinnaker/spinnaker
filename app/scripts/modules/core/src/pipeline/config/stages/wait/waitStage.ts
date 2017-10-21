import { module } from 'angular';

import { IStage } from 'core/domain';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

import { SKIP_WAIT_COMPONENT } from './skipWait.component';
import { WaitExecutionDetails } from './WaitExecutionDetails';
import { WaitExecutionLabel } from './WaitExecutionLabel';

export const WAIT_STAGE = 'spinnaker.core.pipeline.stage.waitStage';

module(WAIT_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
  SKIP_WAIT_COMPONENT,
])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      label: 'Wait',
      description: 'Waits a specified period of time',
      key: 'wait',
      templateUrl: require('./waitStage.html'),
      executionDetailsComponent: WaitExecutionDetails,
      executionConfigSections: ['waitConfig', 'taskStatus'],
      executionLabelComponent: WaitExecutionLabel,
      useCustomTooltip: true,
      strategy: true,
      controller: 'WaitStageCtrl',
      validators: [
        { type: 'requiredField', fieldName: 'waitTime' },
      ],
    });
  }).controller('WaitStageCtrl', (stage: IStage) => stage.waitTime = stage.waitTime || 30);
