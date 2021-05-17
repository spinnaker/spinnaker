import React from 'react';

import { robotToHuman, useForceUpdate, useObservable } from 'core/presentation';
import { ReactInjector } from 'core/reactShims';
import { duration } from 'core/utils';
import { Spinner } from 'core/widgets';

import { StatusGlyph } from '../StatusGlyph';
import { TaskMonitor } from './TaskMonitor';
import { displayableTasks } from '../displayableTasks.filter';

export const TaskMonitorStatus = ({ monitor }: { monitor: TaskMonitor }) => {
  const forceUpdate = useForceUpdate();
  useObservable(monitor.statusUpdatedStream, () => forceUpdate());

  if (!monitor.task) {
    return <ul className="task">submitting task...</ul>;
  }

  return (
    <>
      <ul className="task task-progress">
        {displayableTasks(monitor.task.steps).map((step, i) => (
          <li key={i}>
            <StatusGlyph item={step} /> {robotToHuman(step.name)} {step.startTime && duration(step.runningTimeInMs)}
          </li>
        ))}
      </ul>
      {monitor.task.isRunning && (
        <ul className="task task-progress task-progress-running">
          <li>
            <Spinner size="small" />
          </li>
        </ul>
      )}
      {monitor.task.isCompleted && (
        <ul className="task task-progress task-progress-refresh">
          <li>
            <span className="far fa-check-circle" /> <strong>Operation succeeded!</strong>
          </li>
        </ul>
      )}
      {monitor.task.id && !monitor.error && monitor.application && (
        <p>
          You can{' '}
          <a
            href={ReactInjector.$state.href('home.applications.application.tasks.taskDetails', {
              application: monitor.application.name,
              taskId: monitor.task.id,
            })}
          >
            monitor this task from the Tasks view
          </a>
          .
        </p>
      )}
    </>
  );
};
