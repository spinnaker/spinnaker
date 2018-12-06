import * as React from 'react';
import { get } from 'lodash';
import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';

import { IExecutionStage, ITaskStep } from 'core/domain';
import { robotToHuman, Markdown } from 'core/presentation';
import { EventBus } from 'core/event/EventBus';
import { ReactInjector } from 'core/reactShims';

export interface IStageFailureMessageProps {
  message?: string;
  messages?: string[];
  stage: IExecutionStage;
}

export interface IStageFailureMessageState {
  failedTask?: ITaskStep;
  failedExecutionId?: number;
  failedStageName?: string;
  failedStageId?: number;
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
      let failedStageName = stage.name;
      let failedExecutionId;
      let failedStageId;

      if (!failedTask || (!props.message && props.messages.length === 0)) {
        const exceptionSource: any = get(stage.context, 'exception.source');
        if (exceptionSource) {
          failedStageName = exceptionSource.stageName;
          failedExecutionId = exceptionSource.executionId;
          failedStageId = exceptionSource.stageId;
        } else if (stage.after) {
          const failedStage = stage.after.find(s => s.status === 'TERMINAL' || s.status === 'STOPPED');
          if (failedStage) {
            failedStageName = robotToHuman(failedStage.name);
            failedStageId = failedStage.id;
          }
        }
      }

      return { isFailed: true, failedTask, failedExecutionId, failedStageName, failedStageId };
    }
    return { failedTask: undefined, isFailed: false };
  }

  public componentWillReceiveProps(nextProps: IStageFailureMessageProps) {
    this.setState(this.getState(nextProps));
  }

  public render() {
    const { message, messages } = this.props;
    const { isFailed, failedTask, failedExecutionId, failedStageName, failedStageId } = this.state;
    if (isFailed || failedTask || message || messages.length) {
      const exceptionTitle = isFailed ? (messages.length ? 'Exceptions' : 'Exception') : 'Warning';
      const displayMessages =
        message || !messages.length ? (
          <Markdown message={message || 'No reason provided.'} className="break-word" />
        ) : (
          messages.map((m, i) => <Markdown key={i} message={m || 'No reason provided.'} className="break-word" />)
        );

      if (displayMessages) {
        return (
          <div className="row">
            <div className="col-md-12">
              <div className={`alert ${exceptionTitle === 'Warning' ? 'alert-warning' : 'alert-danger'}`}>
                <div>
                  <h5>
                    {exceptionTitle} {failedTask && <span>( {robotToHuman(failedTask.name)} )</span>}
                  </h5>
                  {displayMessages}
                </div>
              </div>
            </div>
          </div>
        );
      }

      if (failedStageId !== undefined) {
        const currentState = ReactInjector.$state.current;
        const params: any = {
          stageId: failedStageId,
        };
        if (failedExecutionId !== undefined) {
          params.executionId = failedExecutionId;
        }

        return (
          <div className="row">
            <div className="col-md-12">
              <div className="alert alert-danger">
                <div>
                  Stage{' '}
                  <UISref to={currentState.name} params={params}>
                    <a>{failedStageName}</a>
                  </UISref>{' '}
                  failed.
                </div>
              </div>
            </div>
          </div>
        );
      }

      EventBus.publish('stage-failure-message:no-reason', { params: { ...ReactInjector.$state.params } });
    }

    return null;
  }
}
