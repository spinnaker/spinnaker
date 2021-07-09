import { module } from 'angular';

import { EXECUTION_STEP_DETAILS_COMPONENT } from './executionStepDetails.component';
import { DISPLAYABLE_TASKS_FILTER } from '../../../../task/displayableTasks.filter';

export const STAGE_COMMON_MODULE = 'spinnaker.core.pipeline.stage.common';

module(STAGE_COMMON_MODULE, [EXECUTION_STEP_DETAILS_COMPONENT, DISPLAYABLE_TASKS_FILTER]);
