import { module } from 'angular';
import { react2angular } from 'react2angular';
import { PipelineConfigActions } from './PipelineConfigActions';
export const PIPELINE_CONFIG_ACTIONS = 'pinnaker.core.pipeline.config.actions';
module(PIPELINE_CONFIG_ACTIONS, []).component(
  'pipelineConfigActions',
  react2angular(PipelineConfigActions, [
    'hasHistory',
    'loadingHistory',
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
