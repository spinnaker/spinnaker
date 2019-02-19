import { module } from 'angular';

import { DISPLAYABLE_TASKS_FILTER } from 'core/task/displayableTasks.filter';
import { EXECUTION_STEP_DETAILS_COMPONENT } from './executionStepDetails.component';

export const STAGE_COMMON_MODULE = 'spinnaker.core.pipeline.stage.common';

module(STAGE_COMMON_MODULE, [EXECUTION_STEP_DETAILS_COMPONENT, DISPLAYABLE_TASKS_FILTER]);
