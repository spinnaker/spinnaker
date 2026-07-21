import { module } from 'angular';

import { PipelineConfigActions } from './PipelineConfigActions';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';
export const PIPELINE_CONFIG_ACTIONS = 'spinnaker.core.pipeline.config.actions';
module(PIPELINE_CONFIG_ACTIONS, []).component(
  'pipelineConfigActions',
  angularComponentFromReact(PipelineConfigActions, 'pipelineConfigActions', [
    'pipeline',
    'renamePipeline',
    'deletePipeline',
    'enablePipeline',
    'disablePipeline',
    'lockPipeline',
    'unlockPipeline',
    'editPipelineJson',
    'showHistory',
    'exportPipelineTemplate',
  ]),
);
