'use strict';

const angular = require('angular');

import { PARAMETERS } from './parameters.module';

export const CORE_PIPELINE_CONFIG_PARAMETERS_PIPELINE_MODULE = 'spinnaker.core.pipeline.parameters';
export const name = CORE_PIPELINE_CONFIG_PARAMETERS_PIPELINE_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_PARAMETERS_PIPELINE_MODULE, [PARAMETERS]);
