import { module } from 'angular';

import { TaskProgressBar } from './TaskProgressBar';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

('use strict');

export const CORE_TASK_TASKPROGRESSBAR_DIRECTIVE = 'spinnaker.core.task.progressBar.directive';
export const name = CORE_TASK_TASKPROGRESSBAR_DIRECTIVE; // for backwards compatibility
module(CORE_TASK_TASKPROGRESSBAR_DIRECTIVE, []).component(
  'taskProgressBar',
  angularComponentFromReact(TaskProgressBar, 'taskProgressBar', ['task']),
);
