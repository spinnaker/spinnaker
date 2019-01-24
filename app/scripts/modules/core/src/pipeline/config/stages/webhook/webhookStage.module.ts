import { module } from 'angular';

import { WEBHOOK_STAGE } from './webhookStage';
import { STAGE_CORE_MODULE } from '../core/stage.core.module';
import { TIME_FORMATTERS } from 'core/utils/timeFormatters';
import { WEBHOOK_EXECUTION_DETAILS_CONTROLLER } from './webhookExecutionDetails.controller';
import { WEBHOOK_STAGE_ADD_CUSTOM_HEADER_MODAL_CONTROLLER } from './modal/addCustomHeader.controller.modal';

export const WEBHOOK_STAGE_MODULE = 'spinnaker.core.pipeline.stage.webhook';
module(WEBHOOK_STAGE_MODULE, [
  WEBHOOK_STAGE,
  require('../stage.module').name,
  STAGE_CORE_MODULE,
  TIME_FORMATTERS,
  WEBHOOK_EXECUTION_DETAILS_CONTROLLER,
  WEBHOOK_STAGE_ADD_CUSTOM_HEADER_MODAL_CONTROLLER,
]);
