import { module } from 'angular';

import { Registry } from '../../../../registry';

import { UNMATCHED_STAGE_TYPE_STAGE_CTRL } from './unmatchedStageTypeStage.controller';

export const UNMATCHED_STAGE_TYPE_STAGE = 'spinnaker.core.pipeline.stage.unmatchedStageType';
module(UNMATCHED_STAGE_TYPE_STAGE, [UNMATCHED_STAGE_TYPE_STAGE_CTRL]).config(() => {
  Registry.pipeline.registerStage({
    key: 'unmatched',
    synthetic: true,
    templateUrl: require('./unmatchedStageTypeStage.html'),
  });
});
