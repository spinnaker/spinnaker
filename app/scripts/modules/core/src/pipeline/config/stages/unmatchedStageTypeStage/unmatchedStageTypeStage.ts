import {module} from 'angular';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';
import {UNMATCHED_STAGE_TYPE_STAGE_CTRL} from './unmatchedStageTypeStage.controller';

export const UNMATCHED_STAGE_TYPE_STAGE = 'spinnaker.core.pipeline.stage.unmatchedStageType';
module(UNMATCHED_STAGE_TYPE_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
  UNMATCHED_STAGE_TYPE_STAGE_CTRL,
]).config((pipelineConfigProvider: any) => {
  pipelineConfigProvider.registerStage({
    key: 'unmatched',
    synthetic: true,
    templateUrl: require('./unmatchedStageTypeStage.html'),
  });
});

