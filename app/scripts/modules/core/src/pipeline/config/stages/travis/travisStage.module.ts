import { module } from 'angular';

import { TRAVIS_STAGE } from './travisStage';
import { STAGE_CORE_MODULE } from '../core/stage.core.module';
import { TIME_FORMATTERS } from 'core/utils/timeFormatters';
import { TRAVIS_EXECUTION_DETAILS_CONTROLLER } from './travisExecutionDetails.controller';
import { TRAVIS_STAGE_ADD_PARAMETER_MODAL_CONTROLLER } from './modal/addParameter.controller.modal';

export const TRAVIS_STAGE_MODULE = 'spinnaker.core.pipeline.stage.travis';
module(TRAVIS_STAGE_MODULE, [
  TRAVIS_STAGE,
  require('../stage.module').name,
  STAGE_CORE_MODULE,
  TIME_FORMATTERS,
  TRAVIS_EXECUTION_DETAILS_CONTROLLER,
  TRAVIS_STAGE_ADD_PARAMETER_MODAL_CONTROLLER,
]);
