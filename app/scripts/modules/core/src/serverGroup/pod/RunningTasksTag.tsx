import * as React from 'react';
import { IExecution, ITask } from 'core/domain';
import { HoverablePopover } from 'core/presentation';

import { RunningTasksPopoverContent } from './RunningTasksPopoverContent';

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
