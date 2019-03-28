import * as React from 'react';
import { Markdown } from 'core/presentation';
import { ReactInjector } from 'core/reactShims';
import { module } from 'angular';
import { react2angular } from 'react2angular';
import { ITask } from 'core/domain';
import { RawParams } from '@uirouter/core';
import { TrafficGuardHelperLink } from '../TrafficGuardHelperLink';

export interface ITaskMonitorErrorProps {
  errorMessage: string;
  task?: ITask;
}

export class TaskMonitorError extends React.Component<ITaskMonitorErrorProps> {
  private getBaseState() {
    const { $stateParams } = ReactInjector;
    return `home.${$stateParams.project ? 'project' : 'applications'}.application`;
  }

  private getParams(extras: RawParams): RawParams {
    const { project, application } = ReactInjector.$stateParams;
    return { project, application, ...extras };
  }

  public render() {
    const { errorMessage, task } = this.props;
    const { $state } = ReactInjector;
    if (!errorMessage) {
      return null;
    }
    const taskLink = task.id
      ? $state.href(this.getBaseState() + '.tasks.taskDetails', this.getParams({ taskId: task.id }))
      : null;
    return (
      <div className="col-md-12 overlay-modal-error">
        <div className="alert">
          <h4>
            <i className="fa fa-exclamation-triangle" /> Error:
          </h4>
          <p>
            <Markdown message={errorMessage} />
          </p>
          <TrafficGuardHelperLink errorMessage={errorMessage} />
        </div>
        {taskLink && (
          <p>
            <a href={taskLink}>View this failed task in the tasks pane.</a>
          </p>
        )}
      </div>
    );
  }
}

export const TASK_MONITOR_ERROR = 'spinnaker.core.task.monitor.error';
const ngmodule = module(TASK_MONITOR_ERROR, []);

ngmodule.component('taskMonitorError', react2angular(TaskMonitorError, ['errorMessage', 'task']));
