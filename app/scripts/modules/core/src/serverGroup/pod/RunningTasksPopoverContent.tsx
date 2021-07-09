import { orderBy } from 'lodash';
import * as React from 'react';

import { IExecution, IExecutionStage, ITask, ITaskStep } from '../../domain';
import { robotToHuman } from '../../presentation';
import { StatusGlyph } from '../../task/StatusGlyph';
import { displayableTasks } from '../../task/displayableTasks.filter';
import { duration } from '../../utils';

export interface IRunningTasksPopoverContentProps {
  executions: IExecution[];
  tasks: ITask[];
}

interface IItemDetailsProps {
  item: IExecutionStage | ITaskStep;
  transformName?: boolean;
}

export const ItemDetails = ({ item, transformName }: IItemDetailsProps) => (
  <div className="flex-container-h">
    <div className="flex-grow">
      <span className="small">
        <StatusGlyph item={item} />
      </span>
      {transformName ? robotToHuman(item.name) : item.name}
    </div>
    <div className="flex-pull-right">{duration(item.runningTimeInMs)}</div>
  </div>
);

export const RunningTasksPopoverContent = ({ executions, tasks }: IRunningTasksPopoverContentProps) => {
  const sortedTasks = orderBy(tasks || [], ['startTime'], ['asc']);
  return (
    <div className="RunningTasksPopoverContent">
      <div>
        {sortedTasks.map((task, index) => (
          <div key={`task-${index}`}>
            <strong>{task.name}</strong>
            {displayableTasks(task.steps).map((step, i) => (
              <ItemDetails key={`task-step-${i}`} item={step} transformName={true} />
            ))}
          </div>
        ))}
      </div>
      <div>
        {executions.map((execution, index) => (
          <div key={`execution-${index}`}>
            <strong> Pipeline: {execution.name} </strong>
            {(execution.stages || []).map((stage, i) => (
              <ItemDetails key={`execution-stage-${i}`} item={stage} />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
};
