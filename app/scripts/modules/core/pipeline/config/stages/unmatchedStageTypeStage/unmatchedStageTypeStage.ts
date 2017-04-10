import {module} from 'angular';
import {UNMATCHED_STAGE_TYPE_STAGE_CTRL} from './unmatchedStageTypeStage.controller';

export const UNMATCHED_STAGE_TYPE_STAGE = 'spinnaker.core.pipeline.stage.unmatchedStageType';
module(UNMATCHED_STAGE_TYPE_STAGE, [
  require('core/pipeline/config/pipelineConfigProvider.js'),
  UNMATCHED_STAGE_TYPE_STAGE_CTRL,
]).config((pipelineConfigProvider: any) => {
  pipelineConfigProvider.registerStage({
    key: 'unmatched',
    synthetic: true,
    templateUrl: require('./unmatchedStageTypeStage.html'),
  });
});

