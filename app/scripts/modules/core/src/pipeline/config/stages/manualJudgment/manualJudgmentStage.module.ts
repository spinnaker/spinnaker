import { module } from 'angular';

import { MANUAL_JUDGMENT_SERVICE } from './manualJudgment.service';
import { STAGE_COMMON_MODULE } from '../common/stage.common.module';

import './manualJudgmentExecutionDetails.less';

export const MANUAL_JUDGMENT_STAGE_MODULE = 'spinnaker.core.pipeline.stage.manualJudgment';

module(MANUAL_JUDGMENT_STAGE_MODULE, [
  require('../stage.module').name,
  MANUAL_JUDGMENT_SERVICE,
  STAGE_COMMON_MODULE,
  require('./manualJudgmentStage').name,
  require('../../../../notification/modal/editNotification.controller.modal').name,
]);
