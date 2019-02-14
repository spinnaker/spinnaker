import * as React from 'react';
import * as ReactGA from 'react-ga';
import { has } from 'lodash';

import { IExecution } from 'core/domain';
import { HoverablePopover } from 'core/presentation';
import { IScheduler } from 'core/scheduler/SchedulerFactory';
import { SchedulerFactory } from 'core/scheduler';
import { Registry } from 'core/registry';
import { relativeTime, timestamp } from 'core/utils';
import { ISortFilter } from 'core/filterModel';
import { ExecutionState } from 'core/state';
import { SETTINGS } from 'core/config/settings';

import { buildDisplayName } from '../executionBuild/buildDisplayName.filter';
import { ExecutionBuildLink } from '../executionBuild/ExecutionBuildLink';
import { ExecutionUserStatus } from './ExecutionUserStatus';
import { ArtifactList } from './ArtifactList';

import './executionStatus.less';

export interface IExecutionStatusProps {
  execution: IExecution;
  toggleDetails: (stageIndex?: number) => void;
  showingDetails: boolean;
  standalone: boolean;
}

export interface IExecutionStatusState {
  sortFilter: ISortFilter;
  parameters: Array<{ key: string; value: any }>;
  timestamp: string;
}

export class ExecutionStatus extends React.Component<IExecutionStatusProps, IExecutionStatusState> {
  private timestampScheduler: IScheduler;

  constructor(props: IExecutionStatusProps) {
    super(props);

    // these are internal parameters that are not useful to end users
    const strategyExclusions = ['parentPipelineId', 'strategy', 'parentStageId', 'deploymentDetails', 'cloudProvider'];

    let parameters: Array<{ key: string; value: any }> = [];

    const { execution } = this.props;
    if (execution.trigger && execution.trigger.parameters) {
      parameters = Object.keys(execution.trigger.parameters)
        .sort()
        .filter(paramKey => (execution.isStrategy ? !strategyExclusions.includes(paramKey) : true))
        .map((paramKey: string) => {
          return { key: paramKey, value: JSON.stringify(execution.trigger.parameters[paramKey]) };
        });
    }

    this.state = {
      sortFilter: ExecutionState.filterModel.asFilterModel.sortFilter,
      parameters,
      timestamp: relativeTime(this.props.execution.startTime),
    };
  }

  private validateTimestamp(): void {
    const newTimestamp = relativeTime(this.props.execution.startTime);
    if (newTimestamp !== this.state.timestamp) {
      this.setState({ timestamp: newTimestamp });
    }
  }

  public componentDidMount(): void {
    this.timestampScheduler = SchedulerFactory.createScheduler();
    this.timestampScheduler.subscribe(() => this.validateTimestamp());
  }

  public componentWillUnmount(): void {
    this.timestampScheduler.unsubscribe();
  }

  private getExecutionTypeDisplay(): string {
    const trigger = this.props.execution.trigger;
    if (trigger.type === 'manual') {
      return 'Manual Start';
    }
    const config = Registry.pipeline.getTriggerConfig(trigger.type);
    if (config && config.executionTriggerLabel) {
      return config.executionTriggerLabel(trigger);
    }
    return trigger.type;
  }

  private getTriggerExecutionStatus() {
    const { trigger } = this.props.execution;
    const triggerConfig = Registry.pipeline.getTriggerConfig(trigger.type);
    if (triggerConfig) {
      return triggerConfig.executionStatusComponent;
    }
    if (trigger.type === 'manual') {
      return ExecutionUserStatus;
    }
    return null;
  }

  private toggleDetails = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Execution details toggled (Details link)' });
    this.props.toggleDetails();
  };

  public render() {
    const { execution, showingDetails, standalone } = this.props;
    const { trigger } = execution;
    const { artifacts, resolvedExpectedArtifacts } = trigger;
    const TriggerExecutionStatus = this.getTriggerExecutionStatus();
    return (
      <div className="execution-status-section">
        <span className={`trigger-type ${this.state.sortFilter.groupBy !== name ? 'subheading' : ''}`}>
          <h5 className="build-number">
            <ExecutionBuildLink execution={execution} />
          </h5>
          <h5 className={`execution-type ${trigger.dryRun ? 'execution-dry-run' : ''}`}>
            {trigger.dryRun && 'DRY RUN: '}
            {this.getExecutionTypeDisplay()}
          </h5>
        </span>
        <ul className="trigger-details">
          {has(trigger, 'buildInfo.url') && <li>{buildDisplayName(trigger.buildInfo)}</li>}
          {TriggerExecutionStatus && <TriggerExecutionStatus trigger={trigger} />}
          <li>
            <HoverablePopover delayShow={100} delayHide={0} template={<span>{timestamp(execution.startTime)}</span>}>
              {this.state.timestamp}
            </HoverablePopover>
          </li>
          {this.state.parameters.map(p => (
            <li key={p.key} className="break-word">
              <span className="parameter-key">{p.key}</span>: {p.value}
            </li>
          ))}
        </ul>
        {SETTINGS.feature.artifacts && (
          <ArtifactList artifacts={artifacts} resolvedExpectedArtifacts={resolvedExpectedArtifacts} />
        )}
        {!standalone && (
          <a className="clickable" onClick={this.toggleDetails}>
            <span
              className={`small glyphicon ${showingDetails ? 'glyphicon-chevron-down' : 'glyphicon-chevron-right'}`}
            />
            Details
          </a>
        )}
      </div>
    );
  }
}
