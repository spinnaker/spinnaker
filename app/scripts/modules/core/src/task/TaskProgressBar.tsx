import classNames from 'classnames';
import React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { ITask } from '../domain';
import { robotToHuman } from '../presentation/robotToHumanFilter/robotToHuman.filter';

export interface ITaskProgressBarProps {
  task: ITask;
  className?: string;
}

export function TaskProgressBar(props: ITaskProgressBarProps) {
  const { task, className } = props;
  const { steps, id } = task;
  let tooltip;

  if (task.isRunning) {
    const currentStep = steps.find((step) => step.hasNotStarted || step.isRunning);
    if (currentStep) {
      const currentStepIndex = steps.indexOf(currentStep) + 1;
      tooltip = (
        <Tooltip id={id}>{`Step ${currentStepIndex} of ${steps.length}: ${robotToHuman(currentStep.name)}`}</Tooltip>
      );
    }
  }

  if (task.isFailed) {
    const failedStep = steps.find((step) => step.isFailed || step.isSuspended);
    if (failedStep && task.failureMessage) {
      const failedStepIndex = steps.indexOf(failedStep) + 1;
      const ellipses = String.fromCharCode(8230);
      const clipped =
        task.failureMessage.length > 400 ? task.failureMessage.substring(0, 400) + ellipses : task.failureMessage;
      tooltip = (
        <Tooltip id={id}>
          {`Failed on Step ${failedStepIndex} of ${steps.length}:`}
          <br />
          {robotToHuman(failedStep.name)}
          <br />
          <br />
          <strong>Exception:</strong>
          <p>{clipped}</p>
        </Tooltip>
      );
    } else {
      tooltip = <Tooltip id={id}>Task failed; sorry, no reason provided</Tooltip>;
    }
  }

  const stepsComplete = steps.filter((step) => step.isCompleted);

  const progressBarClassName = classNames({
    'progress-bar': true,
    'progress-bar-info': task.isRunning || task.hasNotStarted,
    'progress-bar-success': task.isCompleted,
    'progress-bar-disabled': task.isCanceled,
    'progress-bar-danger': task.isFailed,
  });
  const progressBar = (
    <div className={classNames('progress', className)}>
      <div className={progressBarClassName} style={{ width: `${10 + (stepsComplete.length / steps.length) * 90}%` }} />
    </div>
  );

  if (tooltip) {
    return (
      <OverlayTrigger placement="top" overlay={tooltip}>
        {progressBar}
      </OverlayTrigger>
    );
  }
  return progressBar;
}
