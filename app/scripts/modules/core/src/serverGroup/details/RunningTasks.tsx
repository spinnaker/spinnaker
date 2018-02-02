import * as React from 'react';


import { Application } from 'core/application/application.model';
import { IExecution, IServerGroup, ITask } from 'core/domain';
import { CollapsibleSection, robotToHuman } from 'core/presentation';
import { displayableTasks } from 'core/task/displayableTasks.filter';
import { StatusGlyph } from 'core/task/StatusGlyph';
import { duration } from 'core/utils/timeFormatters';
import { PlatformHealthOverrideMessage } from 'core/task/PlatformHealthOverrideMessage';

export interface IRunningTasksProps {
  application: Application;
  serverGroup: IServerGroup;
}

export class RunningTasks extends React.Component<IRunningTasksProps> {
  public render() {
    const { application, serverGroup } = this.props;

    if ( serverGroup.runningTasks.length > 0 || serverGroup.runningExecutions.length > 0) {
      return (
        <CollapsibleSection heading="Running Tasks" defaultExpanded={true} bodyClassName="details-running-tasks">
          {serverGroup.runningTasks.sort((a, b) => a.startTime - b.startTime).map((task) => (
            <Task key={task.id} task={task} application={application} />
          ))}
          {serverGroup.runningExecutions.map((execution) => (
            <Execution key={execution.id} execution={execution} />
          ))}
        </CollapsibleSection>
      );
    }

    return null;
  }
}

const Task = (props: { task: ITask, application: Application }): JSX.Element => (
  <div className="container-fluid no-padding">
    <div className="row">
      <div className="col-md-12">
        <strong>
          {props.task.name}
        </strong>
      </div>
    </div>
    {displayableTasks(props.task.steps).map((step) => (
      <div className="row">
        <div className="col-md-7 col-md-offset-0">
          <span className="small"><StatusGlyph item={step}/></span>{' '}{robotToHuman(step.name)}
          {step.name === 'waitForUpInstances' && (
            <PlatformHealthOverrideMessage step={step} task={props.task} application={props.application} />
          )}
        </div>
        <div className="col-md-4 text-right">
          {duration(step.runningTimeInMs)}
        </div>
      </div>
    ))}
  </div>
);

const Execution = (props: { execution: IExecution }): JSX.Element => (
  <div className="container-fluid no-padding">
    <div className="row">
      <div className="col-md-12">
        <strong>
          Pipeline: {props.execution.name}
        </strong>
      </div>
    </div>
    {props.execution.stages.map((stage) => (
    <div className="row">
      <div className="col-md-7 col-md-offset-0">
        <span className="small"><StatusGlyph item={stage}/></span> {robotToHuman(stage.name)}
      </div>
      <div className="col-md-4 text-right">
        {duration(stage.runningTimeInMs)}
      </div>
    </div>
  ))}
  </div>
);
