import classnames from 'classnames';
import { flatten, uniq, without } from 'lodash';
import React from 'react';
import { from as observableFrom, Subject, Subscription } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { MigrationTag } from './MigrationTag';
import { AccountTag } from '../../../account';
import { Application } from '../../../application/application.model';
import { CollapsibleSectionStateCache } from '../../../cache';
import { PipelineTemplateReader, PipelineTemplateV2Service } from '../../config/templates';
import {
  IExecution,
  IExecutionGroup,
  IExecutionTrigger,
  IPipeline,
  IPipelineCommand,
  IPipelineTemplateConfigV2,
} from '../../../domain';
import { EntityNotifications } from '../../../entityTag/notifications/EntityNotifications';
import { Execution } from '../execution/Execution';
import { ExecutionAction } from '../executionAction/ExecutionAction';
import { ManualExecutionModal } from '../../manualExecution';
import { Overridable } from '../../../overrideRegistry';
import { Placement } from '../../../presentation/Placement';
import { Popover } from '../../../presentation/Popover';
import { ReactInjector } from '../../../reactShims';
import { ExecutionState } from '../../../state';
import { NextRunTag } from '../../triggers/NextRunTag';
import { TriggersTag } from '../../triggers/TriggersTag';
import { logger } from '../../../utils';
import { RenderWhenVisible } from '../../../utils/RenderWhenVisible';
import { IRetryablePromise } from '../../../utils/retryablePromise';
import { Spinner } from '../../../widgets/spinners/Spinner';

import './executionGroup.less';

const ACCOUNT_TAG_OVERFLOW_LIMIT = 1;

export interface IExecutionGroupProps {
  group: IExecutionGroup;
  application: Application;
  parent: HTMLDivElement;
}

export interface IExecutionGroupState {
  deploymentAccounts: string[];
  pipelineConfig: IPipeline;
  triggeringExecution: boolean;
  open: boolean;
  showingDetails: boolean;
  poll: IRetryablePromise<any>;
  displayExecutionActions: boolean;
  showAccounts: boolean;
  showOverflowAccountTags: boolean;
  placement: Placement;
}

@Overridable('PipelineExecutionGroup')
export class ExecutionGroup extends React.PureComponent<IExecutionGroupProps, IExecutionGroupState> {
  public state: IExecutionGroupState;
  private expandUpdatedSubscription: Subscription;
  private stateChangeSuccessSubscription: Subscription;
  private destroy$ = new Subject();
  private headerRef = React.createRef<HTMLDivElement>();

  constructor(props: IExecutionGroupProps) {
    super(props);
    const { group, application } = props;
    const strategyConfig = application.strategyConfigs.data.find((c: IPipeline) => c.name === group.heading);

    const pipelineConfig = application.pipelineConfigs.data.find((c: IPipeline) => c.name === group.heading);

    const sectionCacheKey = this.getSectionCacheKey();
    const showingDetails = this.isShowingDetails();

    this.state = {
      deploymentAccounts: this.getDeploymentAccounts(),
      triggeringExecution: false,
      showingDetails,
      open:
        showingDetails ||
        !CollapsibleSectionStateCache.isSet(sectionCacheKey) ||
        CollapsibleSectionStateCache.isExpanded(sectionCacheKey),
      poll: null,
      displayExecutionActions: !!(pipelineConfig || strategyConfig),
      showAccounts: ExecutionState.filterModel.asFilterModel.sortFilter.groupBy === 'name',
      pipelineConfig,
      showOverflowAccountTags: false,
      placement: 'top',
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

  private startPipeline(command: IPipelineCommand): PromiseLike<void> {
    const { executionService } = ReactInjector;
    this.setState({ triggeringExecution: true });
    return executionService
      .startAndMonitorPipeline(this.props.application, command.pipelineName, command.trigger)
      .then((monitor) => {
        this.setState({ poll: monitor });
        return monitor.promise;
      })
      .finally(() => {
        this.setState({ triggeringExecution: false });
      });
  }

  public triggerPipeline(trigger: IExecutionTrigger = null, config = this.state.pipelineConfig): void {
    observableFrom(
      new Promise((resolve) => {
        if (PipelineTemplateV2Service.isV2PipelineConfig(config)) {
          PipelineTemplateReader.getPipelinePlan(config as IPipelineTemplateConfigV2)
            .then((plan) => resolve(plan))
            .catch(() => resolve(config));
        } else {
          resolve(config);
        }
      }),
    )
      .pipe(takeUntil(this.destroy$))
      .subscribe((pipeline) =>
        ManualExecutionModal.show({
          pipeline,
          application: this.props.application,
          trigger: trigger,
          currentlyRunningExecutions: this.props.group.runningExecutions,
        })
          .then((command) => this.startPipeline(command))
          .catch(() => {}),
      );
  }

  public componentDidMount(): void {
    const { stateEvents } = ReactInjector;
    this.expandUpdatedSubscription = ExecutionState.filterModel.expandSubject.subscribe((expanded) => {
      if (this.state.open !== expanded) {
        this.toggle();
      }
    });
    this.stateChangeSuccessSubscription = stateEvents.stateChangeSuccess.subscribe(() => {
      // If the heading is collapsed, but we've clicked on a link to an execution in this group, expand the group
      const showingDetails = this.isShowingDetails();
      if (this.isShowingDetails() && !this.state.open) {
        this.toggle();
      }
      if (showingDetails !== this.state.showingDetails) {
        this.setState({ showingDetails });
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

    this.destroy$.next();
  }

  private handleHeadingClicked = (): void => {
    logger.log({
      category: 'Pipeline',
      action: `Group ${this.state.open ? 'collapsed' : 'expanded'}`,
      data: { label: this.props.group.heading },
    });
    this.toggle();
  };

  private handleConfigureClicked = (e: React.MouseEvent<HTMLElement>): void => {
    logger.log({
      category: 'Pipeline',
      action: 'Configure pipeline button clicked',
      data: { label: this.props.group.heading },
    });
    this.configure(this.props.group.config.id);
    e.stopPropagation();
  };

  private handleTriggerClicked = (e: React.MouseEvent<HTMLElement>): void => {
    logger.log({
      category: 'Pipeline',
      action: 'Trigger pipeline button clicked',
      data: { label: this.props.group.heading },
    });
    this.triggerPipeline();
    e.stopPropagation();
  };

  private rerunExecutionClicked = (execution: IExecution, config: IPipeline): void => {
    logger.log({ category: 'Pipeline', action: 'Rerun pipeline button clicked', data: { label: config.name } });
    this.triggerPipeline(execution.trigger, config);
  };

  private getDeploymentAccounts(): string[] {
    return uniq(flatten<string>(this.props.group.executions.map((e: IExecution) => e.deploymentTargets)))
      .sort()
      .filter((a) => !!a);
  }

  private onEnter = (element: HTMLElement): void => {
    // height of the content of the popover
    const { height } = element.lastElementChild.getBoundingClientRect();
    // distance from top to where is located the header
    const headerOffset = this.headerRef.current?.getBoundingClientRect()?.top + window.scrollY;
    this.setState({ placement: headerOffset - height > 0 ? 'top' : 'right' });
  };

  private renderExecutions() {
    const { pipelineConfig } = this.state;
    const { executions } = this.props.group;
    return (
      <>
        {executions.map((execution) => (
          <Execution
            key={execution.id}
            execution={execution}
            pipelineConfig={pipelineConfig}
            application={this.props.application}
            onRerun={pipelineConfig ? this.rerunExecutionClicked : undefined}
          />
        ))}
      </>
    );
  }

  public render(): React.ReactElement<ExecutionGroup> {
    const { group } = this.props;
    const { displayExecutionActions, pipelineConfig, triggeringExecution, showingDetails, placement } = this.state;
    const pipelineDisabled = pipelineConfig && pipelineConfig.disabled;
    const pipelineJustMigrated = pipelineConfig?.migrationStatus === 'Started';
    const pipelineDescription = pipelineConfig && pipelineConfig.description;
    const hasRunningExecutions = group.runningExecutions && group.runningExecutions.length > 0;

    const deploymentAccountLabels = without(
      this.state.deploymentAccounts || [],
      ...(group.targetAccounts || []),
    ).map((account: string) => <AccountTag key={account} account={account} />);
    const groupTargetAccountLabels: React.ReactNode[] = [];
    let groupTargetAccountLabelsExtra: React.ReactNode[] = [];
    if (group.targetAccounts && group.targetAccounts.length > 0) {
      group.targetAccounts.slice(0, ACCOUNT_TAG_OVERFLOW_LIMIT).map((account) => {
        groupTargetAccountLabels.push(<AccountTag key={account} account={account} className="account-tag-wrapper" />);
      });
    }
    if (group.targetAccounts && group.targetAccounts.length > ACCOUNT_TAG_OVERFLOW_LIMIT) {
      groupTargetAccountLabels.push(
        <span
          key="account-overflow"
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
        group.targetAccounts.slice(ACCOUNT_TAG_OVERFLOW_LIMIT).map((account) => {
          return <AccountTag key={account} account={account} />;
        }),
      );
    }

    const shadowedClassName = classnames({ shadowed: true, 'in-migration': pipelineJustMigrated });
    const groupActionsClassName = classnames({
      'text-right': true,
      'execution-group-actions': true,
      'in-migration': pipelineJustMigrated,
    });

    return (
      <div className={`execution-group ${showingDetails ? 'showing-details' : 'details-hidden'}`}>
        {group.heading && (
          <div className="clickable sticky-header" onClick={this.handleHeadingClicked}>
            <div ref={this.headerRef} className={`execution-group-heading ${pipelineDisabled ? 'inactive' : 'active'}`}>
              <span className={`glyphicon pipeline-toggle glyphicon-chevron-${this.state.open ? 'down' : 'right'}`} />
              <div className={shadowedClassName} style={{ position: 'relative' }}>
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
                      <Popover onEnter={this.onEnter} value={pipelineDescription} placement={placement}>
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
                  {pipelineJustMigrated && <MigrationTag />}
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
                {displayExecutionActions && (
                  <div className={groupActionsClassName}>
                    {pipelineConfig && <TriggersTag pipeline={pipelineConfig} />}
                    {pipelineConfig && <NextRunTag pipeline={pipelineConfig} />}
                    <ExecutionAction handleClick={this.handleConfigureClicked}>
                      <span className="glyphicon glyphicon-cog" />
                      {' Configure'}
                    </ExecutionAction>
                    {pipelineConfig && (
                      <ExecutionAction
                        handleClick={this.handleTriggerClicked}
                        style={{ visibility: pipelineDisabled ? 'hidden' : 'visible' }}
                      >
                        {triggeringExecution ? (
                          <div className="horizontal middle inline-spinner">
                            <Spinner size="nano" />
                            <span> Starting Manual Execution</span>
                          </div>
                        ) : (
                          <span>
                            <span className="glyphicon glyphicon-play" /> Start Manual Execution
                          </span>
                        )}
                      </ExecutionAction>
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
              <RenderWhenVisible
                container={this.props.parent}
                disableHide={showingDetails}
                initiallyVisible={showingDetails}
                placeholderHeight={group.executions.length * 110}
                bufferHeight={1000}
                render={() => this.renderExecutions()}
              />
            </div>
          </div>
        )}
      </div>
    );
  }
}
