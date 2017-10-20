import * as React from 'react';

import { IExecutionStage, ITaskStep } from 'core/domain';

import { robotToHuman } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';

export interface IStageFailureMessageProps {
  message?: string;
  messages?: string[];
  stage: IExecutionStage;
}

export interface IStageFailureMessageState {
  failedTask?: ITaskStep;
  isFailed?: boolean;
}

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
      return { isFailed: true, failedTask: (stage.tasks || []).find(t => t.status === 'TERMINAL' || t.status === 'STOPPED') };
    }
    return { failedTask: undefined, isFailed: false };
  }

  public componentWillReceiveProps(nextProps: IStageFailureMessageProps) {
    this.setState(this.getState(nextProps));
  }

  public render() {
    const { message, messages } = this.props;
    const { isFailed, failedTask } = this.state;
    if (isFailed || failedTask || message || messages.length) {
      const exceptionTitle = messages.length ? 'Exceptions' : 'Exception';
      const displayMessages = message || !messages.length ?
        <p>{message || 'No reason provided.'}</p> :
        messages.map((m, i) => <p key={i}>{m || 'No reason provided.'}</p>);

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
    return null;
  }
}
