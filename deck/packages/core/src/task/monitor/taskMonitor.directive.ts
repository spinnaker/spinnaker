import { module } from 'angular';

import { TaskMonitorWrapper } from './TaskMonitorWrapper';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

import './taskMonitor.directive.less';

export const TASKS_MONITOR_DIRECTIVE = 'spinnaker.tasks.monitor.directive';

const ngmodule = module(TASKS_MONITOR_DIRECTIVE, []);

ngmodule.component('taskMonitor', angularComponentFromReact(TaskMonitorWrapper, 'taskMonitor', ['monitor']));
