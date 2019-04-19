import * as React from 'react';
import { has } from 'lodash';

import { IExecution } from 'core/domain';
import { HoverablePopover } from 'core/presentation';
import { IScheduler } from 'core/scheduler/SchedulerFactory';
import { SchedulerFactory } from 'core/scheduler';
import { Registry } from 'core/registry';
import { relativeTime, timestamp } from 'core/utils';
import { ISortFilter } from 'core/filterModel';
import { ExecutionState } from 'core/state';

import { buildDisplayName } from '../executionBuild/buildDisplayName.filter';
import { ExecutionBuildLink } from '../executionBuild/ExecutionBuildLink';
import { ExecutionUserStatus } from './ExecutionUserStatus';

import './executionStatus.less';

export interface IExecutionStatusProps {
  execution: IExecution;
  showingDetails: boolean;
  standalone: boolean;
}

export interface IExecutionStatusState {
  sortFilter: ISortFilter;
  timestamp: string;
}

export class ExecutionStatus extends React.Component<IExecutionStatusProps, IExecutionStatusState> {
  private timestampScheduler: IScheduler;

  constructor(props: IExecutionStatusProps) {
    super(props);

    const { execution } = this.props;

    this.state = {
      sortFilter: ExecutionState.filterModel.asFilterModel.sortFilter,
      timestamp: relativeTime(execution.startTime),
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

  public render() {
    const { execution } = this.props;
    const { trigger, authentication } = execution;
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
          {TriggerExecutionStatus && <TriggerExecutionStatus trigger={trigger} authentication={authentication} />}
          <li>
            <HoverablePopover delayShow={100} delayHide={0} template={<span>{timestamp(execution.startTime)}</span>}>
              {this.state.timestamp}
            </HoverablePopover>
          </li>
        </ul>
      </div>
    );
  }
}
