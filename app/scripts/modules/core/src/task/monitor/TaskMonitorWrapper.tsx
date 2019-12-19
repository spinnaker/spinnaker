import React from 'react';
import { CSSTransition } from 'react-transition-group';

import { TaskMonitor } from 'core/task';
import { useForceUpdateFromObservable } from 'core/presentation/hooks';

import { TaskMonitorStatus } from './TaskMonitorStatus';
import { TaskMonitorError } from './TaskMonitorError';

export interface ITaskMonitorProps {
  monitor: TaskMonitor;
}

export const TaskMonitorWrapper = ({ monitor }: ITaskMonitorProps) => {
  useForceUpdateFromObservable(monitor.statusUpdatedStream);

  if (!monitor.submitting && !monitor.error) {
    return null;
  }

  return (
    <CSSTransition appear={true} classNames="overlay-modal" timeout={0} in={true}>
      <div className="overlay overlay-modal vertical">
        <div className="modal-header">
          <h3>{monitor.title}</h3>
        </div>
        <div className="modal-body clearfix">
          <div className="clearfix">
            {monitor.task && (
              <div className="col-md-8 col-md-offset-2 overlay-modal-status">
                <TaskMonitorStatus monitor={monitor} />
              </div>
            )}
            <TaskMonitorError errorMessage={monitor.errorMessage} task={monitor.task} />
          </div>
        </div>
        {!monitor.error && (
          <div className="modal-footer">
            <button className="btn btn-primary" onClick={monitor.closeModal}>
              Close
            </button>
          </div>
        )}
        {monitor.error && (
          <div className="modal-footer">
            <button className="btn btn-primary" onClick={() => monitor.tryToFix()}>
              Go back and try to fix this
            </button>
            <button className="btn btn-default" onClick={monitor.closeModal}>
              Cancel
            </button>
          </div>
        )}
      </div>
    </CSSTransition>
  );
};
