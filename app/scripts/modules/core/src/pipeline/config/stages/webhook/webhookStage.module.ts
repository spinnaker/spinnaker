import { module } from 'angular';

import { STAGE_COMMON_MODULE } from '../common/stage.common.module';
import { WEBHOOK_STAGE_ADD_CUSTOM_HEADER_MODAL_CONTROLLER } from './modal/addCustomHeader.controller.modal';
import { CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE } from '../stage.module';
import { TIME_FORMATTERS } from '../../../../utils/timeFormatters';
import { WEBHOOK_EXECUTION_DETAILS_CONTROLLER } from './webhookExecutionDetails.controller';
import { WEBHOOK_STAGE } from './webhookStage';

export const WEBHOOK_STAGE_MODULE = 'spinnaker.core.pipeline.stage.webhook';
module(WEBHOOK_STAGE_MODULE, [
  WEBHOOK_STAGE,
  CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE,
  STAGE_COMMON_MODULE,
  TIME_FORMATTERS,
  WEBHOOK_EXECUTION_DETAILS_CONTROLLER,
  WEBHOOK_STAGE_ADD_CUSTOM_HEADER_MODAL_CONTROLLER,
]);
