import {module} from 'angular';

import {WEBHOOK_STAGE} from './webhookStage';
import {IGOR_SERVICE} from 'core/ci/igor.service';
import {WEBHOOK_EXECUTION_DETAILS_CONTROLLER} from './webhookExecutionDetails.controller';

export const WEBHOOK_STAGE_MODULE = 'spinnaker.core.pipeline.stage.webhook';
module(WEBHOOK_STAGE_MODULE, [
  WEBHOOK_STAGE,
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('core/utils/timeFormatters.js'),
  IGOR_SERVICE,
  WEBHOOK_EXECUTION_DETAILS_CONTROLLER,
]);
