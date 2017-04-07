import {module} from 'angular';

import {TRAVIS_STAGE} from './travisStage';
import {IGOR_SERVICE} from 'core/ci/igor.service';
import {TIME_FORMATTERS} from 'core/utils/timeFormatters';
import {TRAVIS_EXECUTION_DETAILS_CONTROLLER} from './travisExecutionDetails.controller';

export const TRAVIS_STAGE_MODULE = 'spinnaker.core.pipeline.stage.travis';
module(TRAVIS_STAGE_MODULE, [
  TRAVIS_STAGE,
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  TIME_FORMATTERS,
  IGOR_SERVICE,
  TRAVIS_EXECUTION_DETAILS_CONTROLLER,
]);
