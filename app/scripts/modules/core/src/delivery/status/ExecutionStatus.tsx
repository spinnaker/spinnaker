import * as React from 'react';
import * as ReactGA from 'react-ga';
import { has } from 'lodash';
import { BindAll } from 'lodash-decorators';

import { IBuildTrigger, ICronTrigger, IDockerTrigger, IExecution } from 'core/domain';
import { IScheduler } from 'core/scheduler/scheduler.factory';
import { ReactInjector } from 'core/reactShims';
import { relativeTime, timestamp } from 'core/utils';

import { buildDisplayName } from '../executionBuild/buildDisplayName.filter';
import { ExecutionBuildNumber } from '../executionBuild/ExecutionBuildNumber';

import './executionStatus.less';

export interface IExecutionStatusProps {
  execution: IExecution;
  toggleDetails: (stageIndex?: number) => void;
  showingDetails: boolean;
  standalone: boolean;
}

export interface IExecutionStatusState {
  sortFilter: any;
  parameters: { key: string, value: any }[];
  timestamp: string;
}

@BindAll()
export class ExecutionStatus extends React.Component<IExecutionStatusProps, IExecutionStatusState> {
  private timestampScheduler: IScheduler;

  constructor(props: IExecutionStatusProps) {
    super(props);

    // these are internal parameters that are not useful to end users
    const strategyExclusions = [
      'parentPipelineId',
      'strategy',
      'parentStageId',
      'deploymentDetails',
      'cloudProvider'
    ];

    let parameters: { key: string, value: any }[] = [];

    const { execution } = this.props;
    if (execution.trigger && execution.trigger.parameters) {
      parameters = Object.keys(execution.trigger.parameters).sort()
        .filter((paramKey) => execution.isStrategy ? !strategyExclusions.includes(paramKey) : true)
        .map((paramKey: string) => {
          return { key: paramKey, value: execution.trigger.parameters[paramKey] };
        });
    }

    this.state = {
      sortFilter: ReactInjector.executionFilterModel.asFilterModel.sortFilter,
      parameters,
      timestamp: relativeTime(this.props.execution.startTime),
    }
  }

  private validateTimestamp(): void {
    const newTimestamp = relativeTime(this.props.execution.startTime);
    if (newTimestamp !== this.state.timestamp) {
      this.setState({timestamp: newTimestamp});
    }
  }

  public componentDidMount(): void {
    this.timestampScheduler = ReactInjector.schedulerFactory.createScheduler();
    this.timestampScheduler.subscribe(this.validateTimestamp);
  }

  public componentWillUnmount(): void {
    this.timestampScheduler.unsubscribe();
  }

  private getExecutionTypeDisplay(): String {
    const trigger = this.props.execution.trigger;
    switch (trigger.type) {
      case 'jenkins': return 'Triggered Build';
      case 'manual': return 'Manual Start';
      case 'pipeline': return 'Pipeline';
      case 'docker': return 'Docker Registry';
      case 'cron': return (trigger as ICronTrigger).cronExpression;
      default: return trigger.type;
    }
  }

  private executionUser(input: IExecution): string {
    if (!input.trigger.user) {
      return 'unknown user';
    }
    let user: string = input.trigger.user;
    if (user === '[anonymous]' && has(input, 'trigger.parentExecution.trigger.user')) {
      user = input.trigger.parentExecution.trigger.user;
    }
    return user;
  }

  private toggleDetails(): void {
    ReactGA.event({category: 'Pipeline', action: 'Execution details toggled (Details link)'});
    this.props.toggleDetails();
  }

  public render() {
    const { execution, showingDetails, standalone } = this.props;
    return (
      <div className="execution-status-section">
        <span className={`trigger-type ${this.state.sortFilter.groupBy !== name ? 'subheading' : ''}`}>
          <h5 className="build-number"><ExecutionBuildNumber execution={execution}/></h5>
          <h5 className="execution-type">{this.getExecutionTypeDisplay()}</h5>
        </span>
        <ul className="trigger-details">
          {has(execution.trigger, 'buildInfo.url') && <li>{buildDisplayName(execution.trigger.buildInfo)}</li>}
          {(execution.trigger as IDockerTrigger).tag && <li>{(execution.trigger as IDockerTrigger).repository}:{(execution.trigger as IDockerTrigger).tag}</li>}

          <span>
            <li>
              {execution.trigger.type === 'jenkins' && (execution.trigger as IBuildTrigger).job}
              {['manual', 'pipeline'].includes(execution.trigger.type) && this.executionUser(execution)}
            </li>
            <li title={timestamp(execution.startTime)}>
              {this.state.timestamp}
            </li>

          </span>
          {this.state.parameters.map((p) => <li key={p.key} className="break-word"><span className="parameter-key">{p.key}</span>: {p.value}</li>)}
        </ul>
        {!standalone && <a className="clickable" onClick={this.toggleDetails}><span className={`small glyphicon ${showingDetails ? 'glyphicon-chevron-down' : 'glyphicon-chevron-right'}`}/>Details</a>}
      </div>
    )
  }
}
