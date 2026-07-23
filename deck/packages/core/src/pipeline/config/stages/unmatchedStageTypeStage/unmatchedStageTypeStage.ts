import { module } from 'angular';

import { UnmatchedStageTypeStageConfig } from './UnmatchedStageTypeStageConfig';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const UNMATCHED_STAGE_TYPE_STAGE = 'spinnaker.core.pipeline.stage.unmatchedStageType';
export const unmatchedStageTypeStage: IStageTypeConfig = {
  key: 'unmatched',
  synthetic: true,
  component: UnmatchedStageTypeStageConfig,
};

Registry.pipeline.registerStage(unmatchedStageTypeStage);
module(UNMATCHED_STAGE_TYPE_STAGE, []);
