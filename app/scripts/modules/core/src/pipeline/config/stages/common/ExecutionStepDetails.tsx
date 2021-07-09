import React from 'react';

import { IExecutionStage } from '../../../../domain';
import { OrchestratedItemRunningTime } from '../../../executions/execution/OrchestratedItemRunningTime';
import { robotToHuman } from '../../../../presentation/robotToHumanFilter/robotToHuman.filter';
import { StatusGlyph } from '../../../../task/StatusGlyph';
import { displayableTasks } from '../../../../task/displayableTasks.filter';
import { duration } from '../../../../utils/timeFormatters';

export interface IExecutionStepDetailsProps {
  item: IExecutionStage;
}

export interface IExecutionStepDetailsState {
  runningTimeInMs: number;
}

export class ExecutionStepDetails extends React.Component<IExecutionStepDetailsProps, IExecutionStepDetailsState> {
  private runningTime: OrchestratedItemRunningTime;

  public componentWillUnmount(): void {
    this.runningTime && this.runningTime.reset();
  }

  public componentDidMount(): void {
    const { item } = this.props;
    const runningTask = (item.tasks || []).find((t) => t.status === 'RUNNING');
    if (runningTask) {
      this.runningTime = new OrchestratedItemRunningTime(runningTask, (time: number) =>
        this.setState({ runningTimeInMs: time }),
      );
    }
  }

  public render() {
    const { item } = this.props;
    return (
      <div className="row">
        <div className="col-md-9">
          <div className="row">
            <div className="col-md-9">
              <strong>Task</strong>
            </div>
            <div className="col-md-3 text-right">
              <strong>Duration</strong>
            </div>
          </div>
          {displayableTasks(item.tasks || []).map((task, index) => (
            <div key={index} className="row">
              <div className="col-md-9">
                <span className="small">
                  <StatusGlyph item={task} />
                </span>{' '}
                {robotToHuman(task.name)}
              </div>
              <div className="col-md-3 text-right">{duration(task.runningTimeInMs)}</div>
            </div>
          ))}
        </div>
      </div>
    );
  }
}
