import * as React from 'react';

import { IExecutionStage, ITaskStep } from 'core/domain';

import { robotToHuman } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';
import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';

import { ReactInjector } from 'core';

export interface IStageFailureMessageProps {
  message?: string;
  messages?: string[];
  stage: IExecutionStage;
}

export interface IStageFailureMessageState {
  failedTask?: ITaskStep;
  failedStage?: IExecutionStage;
  failedStageIndex?: number;
  isFailed?: boolean;
}

@UIRouterContext
export class StageFailureMessage extends React.Component<IStageFailureMessageProps, IStageFailureMessageState> {
  public static defaultProps: Partial<IStageFailureMessageProps> = {
    messages: [],
  };

  constructor(props: IStageFailureMessageProps) {
    super(props);
    this.state = this.getState(props);
  }

  private getState(props: IStageFailureMessageProps): IStageFailureMessageState {
    const { stage } = props;
    if (stage && (stage.isFailed || stage.isStopped)) {
      const failedTask = (stage.tasks || []).find(t => t.status === 'TERMINAL' || t.status === 'STOPPED');
      let failedStage = stage;
      let failedStageIndex = -1;
      if (!failedTask && stage.after) {
        failedStage = stage.after.find(s => s.status === 'TERMINAL' || s.status === 'STOPPED');
        failedStageIndex = stage.after.indexOf(failedStage);
      }

      return { isFailed: true, failedTask, failedStage, failedStageIndex };
    }
    return { failedTask: undefined, isFailed: false };
  }

  public componentWillReceiveProps(nextProps: IStageFailureMessageProps) {
    this.setState(this.getState(nextProps));
  }

  public render() {
    const { message, messages } = this.props;
    const { isFailed, failedTask, failedStage, failedStageIndex } = this.state;
    if (isFailed || failedTask || message || messages.length) {
      const exceptionTitle = messages.length ? 'Exceptions' : 'Exception';
      const displayMessages = message || !messages.length ?
        <p>{message || 'No reason provided.'}</p> :
        messages.map((m, i) => <p key={i}>{m || 'No reason provided.'}</p>);

      if (failedTask || !failedStage) {
        return (
          <div className="row">
            <div className="col-md-12">
              <div className="alert alert-danger">
                <div>
                  <h5>{exceptionTitle} {failedTask && <span>( {robotToHuman(failedTask.name)} )</span>}</h5>
                  {displayMessages}
                </div>
              </div>
            </div>
          </div>
        );
      }

      const currentState = ReactInjector.$state.current;
      return (
        <div className="row">
          <div className="col-md-12">
            <div className="alert alert-danger">
              <div>
                Stage <UISref to={currentState.name} params={{ step: failedStageIndex }}>
                  <a>{failedStage.name}</a>
                </UISref> failed.
              </div>
            </div>
          </div>
        </div>
      );
    }
    return null;
  }
}
