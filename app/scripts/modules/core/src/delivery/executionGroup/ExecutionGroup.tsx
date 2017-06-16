import * as React from 'react';
import * as ReactGA from 'react-ga';
import { $timeout } from 'ngimport';
import { IPromise } from 'angular';
import { Subscription } from 'rxjs';
import { find, flatten, uniq, without } from 'lodash';
import autoBindMethods from 'class-autobind-decorator';

import { Application } from 'core/application/application.model';
import { Execution } from './execution/Execution';
import { IExecution, IExecutionGroup, IPipeline, IPipelineCommand } from 'core/domain';
import { NextRunTag } from 'core/delivery/triggers/NextRunTag';
import { Sticky } from 'core/utils/stickyHeader/Sticky';
import { Tooltip } from 'core/presentation/Tooltip';
import { TriggersTag } from 'core/delivery/triggers/TriggersTag';
import { NgReact, ReactInjector } from 'core/reactShims';

import './executionGroup.less';

export interface IExecutionGroupProps {
  group: IExecutionGroup;
  application: Application;
}

export interface IExecutionGroupState {
  deploymentAccounts: string[];
  pipelineConfig: IPipeline;
  triggeringExecution: boolean;
  open: boolean;
  poll: IPromise<any>;
  canTriggerPipelineManually: boolean;
  canConfigure: boolean;
  showAccounts: boolean;
}

@autoBindMethods
export class ExecutionGroup extends React.Component<IExecutionGroupProps, IExecutionGroupState> {
  private strategyConfig: IPipeline;
  private expandUpdatedSubscription: Subscription;
  private stateChangeSuccessSubscription: Subscription;

  constructor(props: IExecutionGroupProps) {
    super(props);
    const { collapsibleSectionStateCache, executionFilterModel } = ReactInjector;

    this.strategyConfig = find(this.props.application.strategyConfigs.data, { name: this.props.group.heading }) as IPipeline;

    const pipelineConfig = find(this.props.application.pipelineConfigs.data, { name: this.props.group.heading }) as IPipeline;
    const sectionCacheKey = this.getSectionCacheKey();

    this.state = {
      deploymentAccounts: this.getDeploymentAccounts(),
      triggeringExecution: false,
      open: this.isShowingDetails() || !collapsibleSectionStateCache.isSet(sectionCacheKey) || collapsibleSectionStateCache.isExpanded(sectionCacheKey),
      poll: null,
      canTriggerPipelineManually: !!pipelineConfig,
      canConfigure: !!(pipelineConfig || this.strategyConfig),
      showAccounts: executionFilterModel.asFilterModel.sortFilter.groupBy === 'name',
      pipelineConfig,
    };
  }

  private isShowingDetails(): boolean {
    const { $state, $stateParams } = ReactInjector;
    return this.props.group.executions
          .some((execution: IExecution) => execution.id === $stateParams.executionId && $state.includes('**.execution.**'));
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
    const { executionService, executionFilterModel } = ReactInjector;
    return executionService.getSectionCacheKey(executionFilterModel.asFilterModel.sortFilter.groupBy, this.props.application.name, this.props.group.heading);
  };

  private toggle(): void {
    const open = !this.state.open;
    if (this.isShowingDetails()) {
      this.hideDetails();
    }
    ReactInjector.collapsibleSectionStateCache.setExpanded(this.getSectionCacheKey(), open);
    this.setState({open});
  }

  private startPipeline(command: IPipelineCommand): IPromise<void> {
    const { executionService, pipelineConfigService } = ReactInjector;
    this.setState({triggeringExecution: true});
    return pipelineConfigService.triggerPipeline(this.props.application.name, command.pipelineName, command.trigger).then(
      (newPipelineId) => {
        const monitor = executionService.waitUntilNewTriggeredPipelineAppears(this.props.application, newPipelineId);
        monitor.then(() => this.setState({triggeringExecution: false}));
        this.setState({poll: monitor});
      },
      () => this.setState({triggeringExecution: false}));
  }

  public triggerPipeline(): void {
    ReactInjector.modalService.open({
      templateUrl: require('../manualExecution/manualPipelineExecution.html'),
      controller: 'ManualPipelineExecutionCtrl as vm',
      resolve: {
        pipeline: () => this.state.pipelineConfig,
        application: () => this.props.application,
        currentlyRunningExecutions: () => this.props.group.runningExecutions,
      }
    }).result.then((command) => this.startPipeline(command));
  }

  public componentDidMount(): void {
    const { executionFilterModel, stateEvents } = ReactInjector;
    this.expandUpdatedSubscription = executionFilterModel.expandSubject.subscribe((expanded) => {
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
      $timeout.cancel(this.state.poll);
    }
    if (this.expandUpdatedSubscription) {
      this.expandUpdatedSubscription.unsubscribe();
    }
    if (this.stateChangeSuccessSubscription) {
      this.stateChangeSuccessSubscription.unsubscribe();
    }
  }

  private handleHeadingClicked(): void {
    ReactGA.event({category: 'Pipeline', action: `Group ${this.state.open ? 'collapsed' : 'expanded'}`, label: this.props.group.heading});
    this.toggle();
  }

  private handleConfigureClicked(e: React.MouseEvent<HTMLElement>): void {
    ReactGA.event({category: 'Pipeline', action: 'Configure pipeline button clicked', label: this.props.group.heading});
    this.configure(this.props.group.config.id);
    e.stopPropagation();
  }

  private handleTriggerClicked(e: React.MouseEvent<HTMLElement>): void {
    ReactGA.event({category: 'Pipeline', action: 'Trigger pipeline button clicked', label: this.props.group.heading});
    this.triggerPipeline();
    e.stopPropagation();
  }

  public render(): React.ReactElement<ExecutionGroup> {
    const { AccountTag } = NgReact;
    const group = this.props.group;
    const pipelineConfig = this.state.pipelineConfig;
    const pipelineDisabled = pipelineConfig && pipelineConfig.disabled;
    const pipelineDescription = pipelineConfig && pipelineConfig.description;
    const hasRunningExecutions = group.runningExecutions && group.runningExecutions.length > 0;

    const deploymentAccountLabels = without(this.state.deploymentAccounts || [], ...(group.targetAccounts || [])).map((account: string) => <AccountTag key={account} account={account}/>);
    const groupTargetAccountLabels = (group.targetAccounts || []).map((account: string) => <AccountTag key={account} account={account}/>);
    // Adding running time to the key is a hack until we can figure out the redux story for executions
    const executions = (group.executions || []).map((execution: IExecution) => <Execution key={execution.stringVal} execution={execution} application={this.props.application}/>);

    return (
      <div className={`execution-group ${this.isShowingDetails() ? 'showing-details' : 'details-hidden'}`}>
        { group.heading && (
          <Sticky className="clickable sticky-header" onClick={this.handleHeadingClicked} topOffset={-3}>
            <div className={`execution-group-heading ${pipelineDisabled ? 'inactive' : 'active'}`}>
              <span className={`glyphicon pipeline-toggle glyphicon-chevron-${this.state.open ? 'down' : 'right'}`}/>
              <div className="shadowed">
                <div className="heading-tag">
                  {this.state.showAccounts && deploymentAccountLabels}
                  {groupTargetAccountLabels}
                </div>
                <h4 className="execution-group-title">
                  {group.heading}
                  {pipelineDescription && <span> <Tooltip value={pipelineDescription}><span className="glyphicon glyphicon-info-sign"/></Tooltip></span>}
                  {pipelineDisabled && <span> (disabled)</span>}
                  {hasRunningExecutions && <span> <span className="badge">{group.runningExecutions.length}</span></span>}
                </h4>
                { this.state.canConfigure && (
                  <div className="text-right execution-group-actions">
                    {pipelineConfig && <TriggersTag pipeline={pipelineConfig}/>}
                    {pipelineConfig && <NextRunTag pipeline={pipelineConfig}/>}
                    <h4>
                      <a className="btn btn-xs btn-link" onClick={this.handleConfigureClicked}>
                        <span className="glyphicon glyphicon-cog"/>
                        {' Configure'}
                      </a>
                    </h4>
                    { this.state.canTriggerPipelineManually && (
                      <h4 style={{visibility: pipelineDisabled ? 'hidden' : 'visible'}}>
                        <a className="btn btn-xs btn-link" onClick={this.handleTriggerClicked}>
                          { this.state.triggeringExecution ?
                            <span><span className="fa fa-cog fa-spin"/> Starting Manual Execution&hellip;</span> :
                            <span><span className="glyphicon glyphicon-play"/> Start Manual Execution</span>
                          }
                        </a>
                      </h4>
                    )}
                  </div>
                )}
              </div>
            </div>
          </Sticky>
        )
        }
        { this.state.open && (
          <div className="execution-groups">
            <div className="execution-group-container">
              {!group.executions.length && (
                <div style={{paddingBottom: '10px'}}>
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
    return uniq(flatten<string>(this.props.group.executions.map((e: IExecution) => e.deploymentTargets))).sort();
  }
}
