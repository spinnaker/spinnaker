'use strict';

const angular = require('angular');

import { STAGE_COMMON_MODULE } from '../common/stage.common.module';
import { TIME_FORMATTERS } from 'core/utils/timeFormatters';

export const CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE_MODULE = 'spinnaker.core.pipeline.stage.jenkins';
export const name = CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE_MODULE, [
  require('./jenkinsStage').name,
  require('../stage.module').name,
  STAGE_COMMON_MODULE,
  TIME_FORMATTERS,
  require('./jenkinsExecutionDetails.controller').name,
]);
