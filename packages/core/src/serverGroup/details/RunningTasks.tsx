import React from 'react';

import { Application } from '../../application/application.model';
import { IExecution, IServerGroup, ITask } from '../../domain';
import { CollapsibleSection, robotToHuman } from '../../presentation';
import { PlatformHealthOverrideMessage } from '../../task/PlatformHealthOverrideMessage';
import { StatusGlyph } from '../../task/StatusGlyph';
import { displayableTasks } from '../../task/displayableTasks.filter';
import { duration } from '../../utils/timeFormatters';

export interface IRunningTasksProps {
  application: Application;
  serverGroup: IServerGroup;
}

export class RunningTasks extends React.Component<IRunningTasksProps> {
  public render() {
    const { application, serverGroup } = this.props;

    const tasks = serverGroup.runningTasks || [];
    const executions = serverGroup.runningExecutions || [];

    if (tasks.length > 0 || executions.length > 0) {
      return (
        <CollapsibleSection
          heading="Running Tasks"
          defaultExpanded={true}
          bodyClassName="content-body details-running-tasks"
        >
          {tasks
            .sort((a, b) => a.startTime - b.startTime)
            .map((task) => (
              <Task key={task.id} task={task} application={application} />
            ))}
          {executions.map((execution) => (
            <Execution key={execution.id} execution={execution} />
          ))}
        </CollapsibleSection>
      );
    }

    return null;
  }
}

const Task = (props: { task: ITask; application: Application }): JSX.Element => (
  <div>
    <strong>{props.task.name}</strong>
    {displayableTasks(props.task.steps).map((step, index) => (
      <div className="flex-container-h baseline margin-between-sm" key={index}>
        <span className="small">
          <StatusGlyph item={step} />
        </span>
        <div>
          <span>{robotToHuman(step.name)}</span>
          {step.name === 'waitForUpInstances' && (
            <PlatformHealthOverrideMessage step={step} task={props.task} application={props.application} />
          )}
        </div>
        <div className="flex-pull-right">{duration(step.runningTimeInMs)}</div>
      </div>
    ))}
  </div>
);

const Execution = (props: { execution: IExecution }): JSX.Element => (
  <div>
    <strong>Pipeline: {props.execution.name}</strong>
    {props.execution.stages.map((stage) => (
      <div className="flex-container-h baseline margin-between-sm">
        <span className="small">
          <StatusGlyph item={stage} />
        </span>
        <span>{robotToHuman(stage.name)}</span>
        <div className="flex-pull-right">{duration(stage.runningTimeInMs)}</div>
      </div>
    ))}
  </div>
);
