import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TaskMonitorWrapper } from './TaskMonitorWrapper';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

import './taskMonitor.directive.less';

export const TASKS_MONITOR_DIRECTIVE = 'spinnaker.tasks.monitor.directive';

const ngmodule = module(TASKS_MONITOR_DIRECTIVE, []);

ngmodule.component('taskMonitor', react2angular(withErrorBoundary(TaskMonitorWrapper, 'taskMonitor'), ['monitor']));
