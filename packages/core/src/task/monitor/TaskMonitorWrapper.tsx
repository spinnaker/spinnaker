import React, { useEffect } from 'react';
import { Modal } from 'react-bootstrap';
import { CSSTransition } from 'react-transition-group';

import { TaskMonitor } from './TaskMonitor';
import { TaskMonitorError } from './TaskMonitorError';
import { TaskMonitorStatus } from './TaskMonitorStatus';
import { useForceUpdate } from '../../presentation/hooks';

export interface ITaskMonitorProps {
  monitor: TaskMonitor;
}

export const TaskMonitorWrapper = ({ monitor }: ITaskMonitorProps) => {
  const forceUpdate = useForceUpdate();

  useEffect(() => {
    const subscription = monitor?.statusUpdatedStream?.subscribe(() => forceUpdate());
    return () => subscription?.unsubscribe();
  }, [monitor]);

  if (!monitor || (!monitor.submitting && !monitor.error)) {
    return null;
  }

  return (
    <CSSTransition appear={true} classNames="overlay-modal" timeout={0} in={true}>
      <div className="overlay overlay-modal vertical">
        <Modal.Header>
          <Modal.Title>{monitor.title}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className="clearfix">
            {monitor.task && (
              <div className="col-md-8 col-md-offset-2 overlay-modal-status">
                <TaskMonitorStatus monitor={monitor} />
              </div>
            )}
            <TaskMonitorError errorMessage={monitor.errorMessage} task={monitor.task} />
          </div>
        </Modal.Body>
        {!monitor.error && (
          <Modal.Footer>
            <button className="btn btn-primary" onClick={monitor.closeModal}>
              Close
            </button>
          </Modal.Footer>
        )}
        {monitor.error && (
          <Modal.Footer>
            <button className="btn btn-primary" onClick={() => monitor.tryToFix()}>
              Go back and try to fix this
            </button>
            <button className="btn btn-default" onClick={monitor.closeModal}>
              Cancel
            </button>
          </Modal.Footer>
        )}
      </div>
    </CSSTransition>
  );
};
