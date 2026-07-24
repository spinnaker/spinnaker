import { UnmatchedStageTypeStageConfig } from './UnmatchedStageTypeStageConfig';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const unmatchedStageTypeStage: IStageTypeConfig = {
  key: 'unmatched',
  synthetic: true,
  component: UnmatchedStageTypeStageConfig,
};

Registry.pipeline.registerStage(unmatchedStageTypeStage);
