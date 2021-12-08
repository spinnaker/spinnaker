import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';
import { get } from 'lodash';
import React from 'react';

import type { IExecutionStage, ITaskStep } from '../../domain';
import { EventBus } from '../../event/EventBus';
import { Markdown, robotToHuman } from '../../presentation';
import { ReactInjector } from '../../reactShims';
import { TrafficGuardHelperLink } from '../../task/TrafficGuardHelperLink';

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

export enum StageFailureMessages {
  NO_REASON_PROVIDED = 'No reason provided.',
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
      const failedTask = (stage.tasks || []).find((t) => t.status === 'TERMINAL' || t.status === 'STOPPED');
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
          const failedStage = stage.after.find((s) => s.status === 'TERMINAL' || s.status === 'STOPPED');
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
    const { message, messages, stage } = this.props;
    const { isFailed, failedTask, failedExecutionId, failedStageName, failedStageId } = this.state;

    let stageMessages = message || !messages.length ? [message] : messages;
    if (stageMessages.length > 0) {
      const exceptionTitle = isFailed ? (messages.length ? 'Exceptions' : 'Exception') : 'Warning';

      // expression evaluation warnings can get really long and hide actual failure messages, source
      // filter out expression evaluation failure messages if either:
      // - there was a stage failure (and failed expressions don't fail the stage)
      // - expression evaluation was explicitly disabled for the stage(as Orca still processes expressions and populates
      //   warnings when evaluation is disabled disabled)
      const shouldFilterExpressionFailures =
        (isFailed && !stage.context?.failOnFailedExpressions) || stage.context?.skipExpressionEvaluation;

      if (shouldFilterExpressionFailures) {
        stageMessages = stageMessages.filter((m) => !m.startsWith('Failed to evaluate'));

        if (stageMessages.length === 0) {
          // no messages to be displayed after filtering
          return null;
        }
      }

      const displayMessages = stageMessages.map((m, i) => (
        <Markdown key={i} message={m || StageFailureMessages.NO_REASON_PROVIDED} className="break-word" />
      ));

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
                  <TrafficGuardHelperLink errorMessage={message || messages.join(',')} />
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
