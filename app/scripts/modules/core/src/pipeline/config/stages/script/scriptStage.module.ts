import { module } from 'angular';

import { SCRIPT_EXECUTION_DETAILS_CONTROLLER } from './scriptExecutionDetails.controller';

export const SCRIPT_STAGE_MODULE = 'spinnaker.core.pipeline.stage.script';
module(SCRIPT_STAGE_MODULE, [
  require('./scriptStage.js').name,
  SCRIPT_EXECUTION_DETAILS_CONTROLLER,
]);
