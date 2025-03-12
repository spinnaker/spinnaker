import { module } from 'angular';

import { FUNCTION_DATA_SOURCE } from './function.dataSource';
import { FUNCTION_STATES } from './function.states';

export const FUNCTION_MODULE = 'spinnaker.core.function';

module(FUNCTION_MODULE, [FUNCTION_DATA_SOURCE, FUNCTION_STATES]);
