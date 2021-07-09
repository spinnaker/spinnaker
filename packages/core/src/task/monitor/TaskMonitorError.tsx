import { RawParams } from '@uirouter/core';
import React from 'react';

import { TrafficGuardHelperLink } from '../TrafficGuardHelperLink';
import { ITask } from '../../domain';
import { Markdown } from '../../presentation';
import { ReactInjector } from '../../reactShims';

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
