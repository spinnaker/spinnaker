import * as React from 'react';
import * as ReactGA from 'react-ga';
import { IPromise } from 'angular';
import { Subscription } from 'rxjs';
import { find, flatten, uniq, without } from 'lodash';

import { Application } from 'core/application/application.model';
import { CollapsibleSectionStateCache } from 'core/cache';
import { EntityNotifications } from 'core/entityTag/notifications/EntityNotifications';
import { Execution } from '../execution/Execution';
import { IExecution, IExecutionGroup, IExecutionTrigger, IPipeline, IPipelineCommand } from 'core/domain';
import { NextRunTag } from 'core/pipeline/triggers/NextRunTag';
import { Popover } from 'core/presentation/Popover';
import { ExecutionState } from 'core/state';
import { IRetryablePromise } from 'core/utils/retryablePromise';

import { TriggersTag } from 'core/pipeline/triggers/TriggersTag';
import { AccountTag } from 'core/account';
import { ModalInjector, ReactInjector } from 'core/reactShims';
import { PipelineTemplateV2Service } from 'core/pipeline';
import { Spinner } from 'core/widgets/spinners/Spinner';

import './executionGroup.less';

const ACCOUNT_TAG_OVERFLOW_LIMIT = 2;

export interface IExecutionGroupProps {
  group: IExecutionGroup;
  application: Application;
}

export interface IExecutionGroupState {
  deploymentAccounts: string[];
  pipelineConfig: IPipeline;
  triggeringExecution: boolean;
  open: boolean;
  poll: IRetryablePromise<any>;
  canTriggerPipelineManually: boolean;
  canConfigure: boolean;
  showAccounts: boolean;
  showOverflowAccountTags: boolean;
}

export class ExecutionGroup extends React.Component<IExecutionGroupProps, IExecutionGroupState> {
  public state: IExecutionGroupState;
  private expandUpdatedSubscription: Subscription;
  private stateChangeSuccessSubscription: Subscription;

  constructor(props: IExecutionGroupProps) {
    super(props);

    const strategyConfig = find(this.props.application.strategyConfigs.data, {
      name: this.props.group.heading,
    }) as IPipeline;

    const pipelineConfig = find(this.props.application.pipelineConfigs.data, {
      name: this.props.group.heading,
    }) as IPipeline;

    const hasNonMPTV2PipelineConfig = pipelineConfig && !PipelineTemplateV2Service.isV2PipelineConfig(pipelineConfig);
    const sectionCacheKey = this.getSectionCacheKey();

    this.state = {
      deploymentAccounts: this.getDeploymentAccounts(),
      triggeringExecution: false,
      open:
        this.isShowingDetails() ||
        !CollapsibleSectionStateCache.isSet(sectionCacheKey) ||
        CollapsibleSectionStateCache.isExpanded(sectionCacheKey),
      poll: null,
      canTriggerPipelineManually: hasNonMPTV2PipelineConfig,
      canConfigure: !!(hasNonMPTV2PipelineConfig || strategyConfig),
      showAccounts: ExecutionState.filterModel.asFilterModel.sortFilter.groupBy === 'name',
      pipelineConfig,
      showOverflowAccountTags: false,
    };
  }

  private isShowingDetails(): boolean {
    const { $state, $stateParams } = ReactInjector;
    return this.props.group.executions.some(
      (execution: IExecution) => execution.id === $stateParams.executionId && $state.includes('**.execution.**'),
    );
  }

  public configure(id: string): void {
    const { $state } = ReactInjector;
    if (!$state.current.name.includes('.executions.execution')) {
      $state.go('^.pipelineConfig', { pipelineId: id });
    } else {
      $state.go('^.^.pipelineConfig', { pipelineId: id });
    }
  }

  private hideDetails(): void {
    ReactInjector.$state.go('.^');
  }

  private getSectionCacheKey(): string {
    const { executionService } = ReactInjector;
    return executionService.getSectionCacheKey(
      ExecutionState.filterModel.asFilterModel.sortFilter.groupBy,
      this.props.application.name,
      this.props.group.heading,
    );
  }

  private toggle = (): void => {
    const open = !this.state.open;
    if (this.isShowingDetails()) {
      this.hideDetails();
    }
    CollapsibleSectionStateCache.setExpanded(this.getSectionCacheKey(), open);
    this.setState({ open });
  };

  private startPipeline(command: IPipelineCommand): IPromise<void> {
    const { executionService } = ReactInjector;
    this.setState({ triggeringExecution: true });
    return executionService
      .startAndMonitorPipeline(this.props.application, command.pipelineName, command.trigger)
      .then(monitor => {
        this.setState({ poll: monitor });
        return monitor.promise;
      })
      .finally(() => {
        this.setState({ triggeringExecution: false });
      });
  }

  public triggerPipeline(trigger: IExecutionTrigger = null, config = this.state.pipelineConfig): void {
    ModalInjector.modalService
      .open({
        templateUrl: require('../../manualExecution/manualPipelineExecution.html'),
        controller: 'ManualPipelineExecutionCtrl as vm',
        resolve: {
          pipeline: () => config,
          application: () => this.props.application,
          currentlyRunningExecutions: () => this.props.group.runningExecutions,
          trigger: () => trigger,
        },
      })
      .result.then(command => this.startPipeline(command))
      .catch(() => {});
  }

  public componentDidMount(): void {
    const { stateEvents } = ReactInjector;
    this.expandUpdatedSubscription = ExecutionState.filterModel.expandSubject.subscribe(expanded => {
      if (this.state.open !== expanded) {
        this.toggle();
      }
    });
    this.stateChangeSuccessSubscription = stateEvents.stateChangeSuccess.subscribe(() => {
      // If the heading is collapsed, but we've clicked on a link to an execution in this group, expand the group
      if (this.isShowingDetails() && !this.state.open) {
        this.toggle();
      }
    });
  }

  public componentWillUnmount(): void {
    if (this.state.poll) {
      this.state.poll.cancel();
    }
    if (this.expandUpdatedSubscription) {
      this.expandUpdatedSubscription.unsubscribe();
    }
    if (this.stateChangeSuccessSubscription) {
      this.stateChangeSuccessSubscription.unsubscribe();
    }
  }

  private handleHeadingClicked = (): void => {
    ReactGA.event({
      category: 'Pipeline',
      action: `Group ${this.state.open ? 'collapsed' : 'expanded'}`,
      label: this.props.group.heading,
    });
    this.toggle();
  };

  private handleConfigureClicked = (e: React.MouseEvent<HTMLElement>): void => {
    ReactGA.event({
      category: 'Pipeline',
      action: 'Configure pipeline button clicked',
      label: this.props.group.heading,
    });
    this.configure(this.props.group.config.id);
    e.stopPropagation();
  };

  private handleTriggerClicked = (e: React.MouseEvent<HTMLElement>): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Trigger pipeline button clicked', label: this.props.group.heading });
    this.triggerPipeline();
    e.stopPropagation();
  };

  private rerunExecutionClicked = (execution: IExecution, config: IPipeline): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Rerun pipeline button clicked', label: config.name });
    this.triggerPipeline(execution.trigger, config);
  };

  public render(): React.ReactElement<ExecutionGroup> {
    const { group } = this.props;
    const { canTriggerPipelineManually, pipelineConfig } = this.state;
    const pipelineDisabled = pipelineConfig && pipelineConfig.disabled;
    const pipelineDescription = pipelineConfig && pipelineConfig.description;
    const hasRunningExecutions = group.runningExecutions && group.runningExecutions.length > 0;

    const deploymentAccountLabels = without(this.state.deploymentAccounts || [], ...(group.targetAccounts || [])).map(
      (account: string) => <AccountTag key={account} account={account} />,
    );
    const groupTargetAccountLabels: React.ReactNode[] = [];
    let groupTargetAccountLabelsExtra: React.ReactNode[] = [];
    if (group.targetAccounts && group.targetAccounts.length > 0) {
      group.targetAccounts.slice(0, ACCOUNT_TAG_OVERFLOW_LIMIT).map(account => {
        groupTargetAccountLabels.push(<AccountTag key={account} account={account} />);
      });
    }
    if (group.targetAccounts && group.targetAccounts.length > ACCOUNT_TAG_OVERFLOW_LIMIT) {
      groupTargetAccountLabels.push(
        <span
          key="foo"
          onMouseEnter={() => this.setState({ showOverflowAccountTags: true })}
          onMouseLeave={() => this.setState({ showOverflowAccountTags: false })}
        >
          <AccountTag
            className="overflow-marker"
            account={`(+ ${group.targetAccounts.length - ACCOUNT_TAG_OVERFLOW_LIMIT} more)`}
          />
        </span>,
      );
      groupTargetAccountLabelsExtra = groupTargetAccountLabelsExtra.concat(
        group.targetAccounts.slice(ACCOUNT_TAG_OVERFLOW_LIMIT).map(account => {
          return <AccountTag key={account} account={account} />;
        }),
      );
    }
    const executions = (group.executions || []).map((execution: IExecution) => (
      <Execution
        key={execution.id}
        execution={execution}
        application={this.props.application}
        onRerun={canTriggerPipelineManually ? this.rerunExecutionClicked : undefined}
      />
    ));

    return (
      <div className={`execution-group ${this.isShowingDetails() ? 'showing-details' : 'details-hidden'}`}>
        {group.heading && (
          <div className="clickable sticky-header" onClick={this.handleHeadingClicked}>
            <div className={`execution-group-heading ${pipelineDisabled ? 'inactive' : 'active'}`}>
              <span className={`glyphicon pipeline-toggle glyphicon-chevron-${this.state.open ? 'down' : 'right'}`} />
              <div className="shadowed" style={{ position: 'relative' }}>
                <div className={`heading-tag-overflow-group ${this.state.showOverflowAccountTags ? 'shown' : ''}`}>
                  {groupTargetAccountLabelsExtra}
                </div>
                <div className="heading-tag collapsing-heading-tags">
                  {this.state.showAccounts && deploymentAccountLabels}
                  {groupTargetAccountLabels}
                </div>
                <h4 className="execution-group-title">
                  {group.fromTemplate && <i className="from-template fa fa-table" title="Pipeline from template" />}
                  {group.heading}
                  {pipelineDescription && (
                    <span>
                      {' '}
                      <Popover value={pipelineDescription}>
                        <span className="glyphicon glyphicon-info-sign" />
                      </Popover>
                    </span>
                  )}
                  {pipelineDisabled && <span> (disabled)</span>}
                  {hasRunningExecutions && (
                    <span>
                      {' '}
                      <span className="badge">{group.runningExecutions.length}</span>
                    </span>
                  )}
                </h4>
                {pipelineConfig && (
                  <EntityNotifications
                    entity={pipelineConfig}
                    application={this.props.application}
                    entity-type="pipeline"
                    hOffsetPercent="20%"
                    placement="top"
                    onUpdate={() => this.props.application.refresh()}
                  />
                )}
                {this.state.canConfigure && (
                  <div className="text-right execution-group-actions">
                    {pipelineConfig && <TriggersTag pipeline={pipelineConfig} />}
                    {pipelineConfig && <NextRunTag pipeline={pipelineConfig} />}
                    <h4>
                      <a className="btn btn-xs btn-link" onClick={this.handleConfigureClicked}>
                        <span className="glyphicon glyphicon-cog" />
                        {' Configure'}
                      </a>
                    </h4>
                    {this.state.canTriggerPipelineManually && (
                      <h4 style={{ visibility: pipelineDisabled ? 'hidden' : 'visible' }}>
                        <a className="btn btn-xs btn-link" onClick={this.handleTriggerClicked}>
                          {this.state.triggeringExecution ? (
                            <div className="horizontal middle inline-spinner">
                              <Spinner size="nano" />
                              <span> Starting Manual Execution</span>
                            </div>
                          ) : (
                            <span>
                              <span className="glyphicon glyphicon-play" /> Start Manual Execution
                            </span>
                          )}
                        </a>
                      </h4>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
        {this.state.open && (
          <div className="execution-groups">
            <div className="execution-group-container">
              {!group.executions.length && (
                <div style={{ paddingBottom: '10px' }}>
                  <em>No executions found matching the selected filters.</em>
                </div>
              )}
              {executions}
            </div>
          </div>
        )}
      </div>
    );
  }

  private getDeploymentAccounts(): string[] {
    return uniq(flatten<string>(this.props.group.executions.map((e: IExecution) => e.deploymentTargets)))
      .sort()
      .filter(a => !!a);
  }
}
