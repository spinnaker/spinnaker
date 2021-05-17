import React from 'react';
import { Modal } from 'react-bootstrap';

import { TaskMonitor } from './TaskMonitor';
import { TaskMonitorError } from './TaskMonitorError';
import { TaskMonitorStatus } from './TaskMonitorStatus';

import './multiTaskMonitor.component.less';

interface IMultiTaskMonitorProps {
  monitors: TaskMonitor[];
  title: string;
  closeModal: () => void;
}

export const MultiTaskMonitor = ({ monitors = [], title, closeModal }: IMultiTaskMonitorProps) => {
  const [hasErrors, setHasErrors] = React.useState(monitors.some((monitor) => monitor.error));
  const isVisible = monitors.some((monitor) => monitor.submitting || monitor.error);
  const clearErrors = (): void => {
    monitors.forEach((monitor) => (monitor.error = null));
    setHasErrors(false);
  };

  if (!isVisible) {
    return null;
  }

  return (
    <div className="overlay overlay-modal multi-task-monitor">
      <Modal.Header>
        <Modal.Title>{title}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <div className="clearfix">
          {monitors.map((monitor, idx) => (
            <div className="col-md-6 overlay-modal-status" key={`${monitor.task?.id ?? idx}`}>
              <h4>{monitor.title}</h4>
              <TaskMonitorStatus monitor={monitor} />
              <TaskMonitorError task={monitor.task} errorMessage={monitor.errorMessage} />
            </div>
          ))}
        </div>
      </Modal.Body>
      <Modal.Footer>
        {!hasErrors && (
          <button className="btn btn-primary" onClick={closeModal}>
            Close
          </button>
        )}
        {hasErrors && (
          <>
            <button className="btn btn-primary" onClick={clearErrors}>
              Go back and try to fix this
            </button>
            <button className="btn btn-default" onClick={closeModal}>
              Cancel
            </button>
          </>
        )}
      </Modal.Footer>
    </div>
  );
};
