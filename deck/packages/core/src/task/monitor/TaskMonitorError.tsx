import type { RawParams } from '@uirouter/core';
import React from 'react';

import { TrafficGuardHelperLink } from '../TrafficGuardHelperLink';
import type { ITask } from '../../domain';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { Markdown } from '../../presentation';

export interface ITaskMonitorErrorProps {
  errorMessage: string;
  task?: ITask;
}

class TaskMonitorErrorComponent extends React.Component<ITaskMonitorErrorProps & IRouterInjectedProps> {
  private getBaseState() {
    return `home.${this.props.stateParams.project ? 'project' : 'applications'}.application`;
  }

  private getParams(extras: RawParams): RawParams {
    const { project, application } = this.props.stateParams;
    return { project, application, ...extras };
  }

  public render() {
    const { errorMessage, task } = this.props;
    if (!errorMessage) {
      return null;
    }
    const taskLink = task.id
      ? this.props.stateService.href(this.getBaseState() + '.tasks.taskDetails', this.getParams({ taskId: task.id }))
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

export const TaskMonitorError = withRouter(TaskMonitorErrorComponent);
TaskMonitorError.displayName = 'TaskMonitorError';
