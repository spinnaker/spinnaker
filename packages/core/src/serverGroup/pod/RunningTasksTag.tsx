import * as React from 'react';

import { RunningTasksPopoverContent } from './RunningTasksPopoverContent';
import { IExecution, ITask } from '../../domain';
import { HoverablePopover } from '../../presentation';

export interface IRunningTasksTagProps {
  executions: IExecution[];
  tasks: ITask[];
}

export const RunningTasksTag = ({ executions, tasks }: IRunningTasksTagProps) => {
  const runningExecutions = (executions || []).filter((e) => e.isRunning || e.hasNotStarted);

  const PopoverContent = React.useCallback(
    () => <RunningTasksPopoverContent executions={runningExecutions} tasks={tasks} />,
    [tasks, runningExecutions],
  );

  if (!tasks?.length && !runningExecutions.length) {
    return null;
  }

  return (
    <span className="RunningTasksTag">
      <HoverablePopover Component={PopoverContent} className="menu-running-tasks">
        <span className="icon">
          <span className="glyphicon icon-spinner fa-spin-slow"></span>
        </span>
      </HoverablePopover>
    </span>
  );
};
