import * as React from 'react';

import {IExecution, IExecutionStage} from 'core/domain';
import {ButtonBusyIndicator} from 'core/forms/buttonBusyIndicator/ButtonBusyIndicator';
import {Application} from 'core/application/application.model';
import {confirmationModalService} from 'core/confirmationModal/confirmationModal.service';
import {executionService} from 'core/delivery/service/execution.service';
import {duration} from 'core/utils/timeFormatters';
import {OrchestratedItemRunningTime} from 'core/delivery/executionGroup/execution/OrchestratedItemRunningTime';

interface IProps {
  execution: IExecution;
  stage: IExecutionStage;
  application: Application;
}

interface IState {
  submitting: boolean;
  remainingWait?: string;
}

export class SkipWait extends React.Component<IProps, IState> {
  private runningTime: OrchestratedItemRunningTime;

  constructor(props: IProps) {
    super(props);
    this.state = {
      submitting: false,
    };
  }

  private setRemainingWait = (time: number): void => {
    this.setState({remainingWait: duration(this.props.stage.context.waitTime * 1000 - time) });
  };

  private skipRemainingWait = (): void => {
    const stage = this.props.stage;
    const matcher = (execution: IExecution) => {
      const match = execution.stages.find((test) => test.id === stage.id);
      return match.status !== 'RUNNING';
    };

    const data = { skipRemainingWait: true };
    confirmationModalService.confirm({
      header: 'Really skip wait?',
      buttonText: 'Skip',
      body: '<p>The pipeline will proceed immediately, marking this stage completed.</p>',
      submitMethod: () => {
        this.setState({submitting: true});
        return executionService.patchExecution(this.props.execution.id, stage.id, data)
          .then(() => executionService.waitUntilExecutionMatches(this.props.execution.id, matcher));
      }
    });
  };

  public componentDidMount() {
    this.runningTime = new OrchestratedItemRunningTime(this.props.stage, (time: number) => this.setRemainingWait(time));
  }

  public componentWillReceiveProps() {
    this.runningTime.checkStatus();
  }

  public componentWillUnmount() {
    this.runningTime.reset();
  }

  public render() {
    const stage = this.props.stage;
    return (
      <div>
        <div>
          <b>Wait time: </b>
          {stage.context.waitTime} seconds
          { stage.context.skipRemainingWait && (
            <span>(skipped after {duration(stage.runningTimeInMs)})</span>
          )}
        </div>
        { stage.isRunning && (
          <div>
            <div>
              <b>Remaining: </b>
              {this.state.remainingWait}
            </div>
            <div>
              <button className="btn btn-xs btn-primary" onClick={this.skipRemainingWait}>
                {this.state.submitting && (<ButtonBusyIndicator/>)}
                {!this.state.submitting && (
                  <span style={{marginRight: '5px'}} className="small glyphicon glyphicon-fast-forward"/>
                )}
                Skip remaining wait
              </button>
            </div>
          </div>
        )}
      </div>
    )
  }
}
