import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TaskProgressBar } from './TaskProgressBar';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

('use strict');

export const CORE_TASK_TASKPROGRESSBAR_DIRECTIVE = 'spinnaker.core.task.progressBar.directive';
export const name = CORE_TASK_TASKPROGRESSBAR_DIRECTIVE; // for backwards compatibility
module(CORE_TASK_TASKPROGRESSBAR_DIRECTIVE, []).component(
  'taskProgressBar',
  react2angular(withErrorBoundary(TaskProgressBar, 'taskProgressBar'), ['task']),
);
