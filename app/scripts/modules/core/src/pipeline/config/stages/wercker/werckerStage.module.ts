import { module } from 'angular';

import { WERCKER_STAGE } from './werckerStage';
import { STAGE_CORE_MODULE } from '../core/stage.core.module';
import { TIME_FORMATTERS } from 'core/utils/timeFormatters';
import { WERCKER_EXECUTION_DETAILS_CONTROLLER } from './werckerExecutionDetails.controller';
import { WERCKER_STAGE_ADD_PARAMETER_MODAL_CONTROLLER } from './modal/addParameter.controller.modal';

export const WERCKER_STAGE_MODULE = 'spinnaker.core.pipeline.stage.wercker';
module(WERCKER_STAGE_MODULE, [
  WERCKER_STAGE,
  require('../stage.module').name,
  STAGE_CORE_MODULE,
  TIME_FORMATTERS,
  WERCKER_EXECUTION_DETAILS_CONTROLLER,
  WERCKER_STAGE_ADD_PARAMETER_MODAL_CONTROLLER,
]);
