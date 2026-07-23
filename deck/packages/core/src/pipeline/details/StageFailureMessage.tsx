import type { StateService } from '@uirouter/core';
import { UISref } from '@uirouter/react';
import { get } from 'lodash';
import React from 'react';

import type { IExecutionStage, ITaskStep } from '../../domain';
import { EventBus } from '../../event/EventBus';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { Overridable } from '../../overrideRegistry';
import { Markdown, robotToHuman } from '../../presentation';
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

export function getStageFailureRoute(
  stateService: StateService,
  failedExecutionId: number,
  failedStageId: number,
): { state: string; params: { executionId?: number; stageId: number } } {
  const params: { executionId?: number; stageId: number } = { stageId: failedStageId };
  if (failedExecutionId !== undefined) {
    params.executionId = failedExecutionId;
  }
  return { state: stateService.current.name, params };
}

@Overridable('stageFailureMessage')
export class StageFailureMessageComponent extends React.Component<
  IStageFailureMessageProps & IRouterInjectedProps,
  IStageFailureMessageState
> {
  public static defaultProps: Partial<IStageFailureMessageProps> = {
    messages: [],
  };

  constructor(props: IStageFailureMessageProps & IRouterInjectedProps) {
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
    const { message, messages } = this.props;
    const { isFailed, failedTask, failedExecutionId, failedStageName, failedStageId } = this.state;

    if (isFailed || failedTask || message || messages.length) {
      const exceptionTitle = isFailed ? (messages.length ? 'Exceptions' : 'Exception') : 'Warning';
      const displayMessages =
        message || !messages.length ? (
          <Markdown message={message || StageFailureMessages.NO_REASON_PROVIDED} className="break-word" />
        ) : (
          messages.map((m, i) => (
            <Markdown key={i} message={m || StageFailureMessages.NO_REASON_PROVIDED} className="break-word" />
          ))
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
                  <TrafficGuardHelperLink errorMessage={message || messages.join(',')} />
                </div>
              </div>
            </div>
          </div>
        );
      }

      if (failedStageId !== undefined) {
        const route = getStageFailureRoute(this.props.stateService, failedExecutionId, failedStageId);

        return (
          <div className="row">
            <div className="col-md-12">
              <div className="alert alert-danger">
                <div>
                  Stage{' '}
                  <UISref to={route.state} params={route.params}>
                    <a>{failedStageName}</a>
                  </UISref>{' '}
                  failed.
                </div>
              </div>
            </div>
          </div>
        );
      }

      EventBus.publish('stage-failure-message:no-reason', { params: { ...this.props.stateParams } });
    }

    return null;
  }
}

export const StageFailureMessage = withRouter<IStageFailureMessageProps & IRouterInjectedProps>(
  StageFailureMessageComponent,
);
