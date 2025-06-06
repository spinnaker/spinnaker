import React from 'react';
import type { Subscription } from 'rxjs';

import { ExecutionGroup } from './ExecutionGroup';
import type { Application } from '../../../application/application.model';
import { BannerContainer } from '../../../banner';
import type { IExecutionGroup } from '../../../domain';
import { ExecutionFilterService } from '../../filter/executionFilter.service';
import { ReactInjector } from '../../../reactShims';
import { ExecutionState } from '../../../state';

import './executionGroups.less';

export interface IExecutionGroupsProps {
  application: Application;
}

export interface IExecutionGroupsState {
  groups: IExecutionGroup[];
  showingDetails: boolean;
  container?: HTMLDivElement; // need to pass the container down to children to use as root for IntersectionObserver
}

export class ExecutionGroups extends React.Component<IExecutionGroupsProps, IExecutionGroupsState> {
  private applicationRefreshUnsubscribe: () => void;
  private groupsUpdatedSubscription: Subscription;
  private stateChangeSuccessSubscription: Subscription;

  constructor(props: IExecutionGroupsProps) {
    super(props);
    const { stateEvents } = ReactInjector;
    this.state = {
      groups: ExecutionState.filterModel.asFilterModel.groups,
      showingDetails: ReactInjector.$state.includes('**.execution'),
    };

    this.applicationRefreshUnsubscribe = props.application.executions.onRefresh(null, () => {
      ExecutionFilterService.updateExecutionGroups(props.application);
    });

    this.groupsUpdatedSubscription = ExecutionFilterService.groupsUpdatedStream.subscribe(() => {
      const newGroups = ExecutionState.filterModel.asFilterModel.groups;
      const { groups } = this.state;
      if (newGroups.length !== groups.length || newGroups.some((g, i) => groups[i] !== g)) {
        this.setState({ groups: newGroups });
      }
    });
    this.stateChangeSuccessSubscription = stateEvents.stateChangeSuccess.subscribe(() => {
      const detailsShown = this.showingDetails();
      if (detailsShown !== this.state.showingDetails) {
        this.setState({ showingDetails: detailsShown });
      }
    });
  }

  private showingDetails(): boolean {
    const { executionId } = ReactInjector.$stateParams;
    // showingDetails() is just used to set a class ('.showing-details') on the wrapper around the execution groups.
    // the effect of this class is that, when an execution is deep linked, all the other execution groups have a partial
    // opacity (except when hovering over them).
    // Here, we are checking if there is an executionId deep linked - and also confirming it's actually present
    // on screen. If not, we will not apply the '.showing-details' class to the wrapper.
    if (!executionId || this.state.groups.every((g) => g.executions.every((e) => e.id !== executionId))) {
      return false;
    }
    return ReactInjector.$state.includes('**.execution');
  }

  private setContainer = (container: HTMLDivElement) => {
    if (this.state.container !== container) {
      this.setState({ container });
    }
  };

  public componentWillUnmount(): void {
    if (this.applicationRefreshUnsubscribe) {
      this.applicationRefreshUnsubscribe();
      this.applicationRefreshUnsubscribe = undefined;
    }
    if (this.groupsUpdatedSubscription) {
      this.groupsUpdatedSubscription.unsubscribe();
    }
    if (this.stateChangeSuccessSubscription) {
      this.stateChangeSuccessSubscription.unsubscribe();
    }
  }

  public render(): React.ReactElement<ExecutionGroups> {
    const { groups = [], container, showingDetails } = this.state;
    const hasGroups = groups.length > 0;
    const className = `row pipelines executions ${showingDetails ? 'showing-details' : ''}`;

    // Check if there are any duplicate execution IDs before applying deduplication
    const allExecutionIds = groups.flatMap((group) => group.executions.map((e) => e.id));
    const uniqueIds = new Set(allExecutionIds);
    const hasDuplicates = allExecutionIds.length !== uniqueIds.size;

    let processedGroups;

    if (hasDuplicates) {
      // Only apply deduplication if there are actually duplicate executions
      const processedExecutionIds = new Set<string>();
      const deduplicatedGroups = groups.map((group) => ({
        ...group,
        executions: [...group.executions],
        runningExecutions: [...group.runningExecutions],
      }));

      deduplicatedGroups.forEach((group) => {
        group.executions = group.executions.filter((execution) => {
          if (processedExecutionIds.has(execution.id)) return false;
          processedExecutionIds.add(execution.id);
          return true;
        });

        group.runningExecutions = group.runningExecutions.filter((execution) =>
          group.executions.some((e) => e.id === execution.id),
        );
      });

      processedGroups = deduplicatedGroups.filter((group) => group.executions.length > 0);
    } else {
      // No duplicates, use the original groups
      processedGroups = groups;
    }

    const allGroups = (processedGroups || [])
      .filter((g: IExecutionGroup) => g?.config?.migrationStatus === 'Started')
      .concat(processedGroups.filter((g) => g?.config?.migrationStatus !== 'Started'));

    const executionGroups = allGroups.map((group: IExecutionGroup) => (
      <ExecutionGroup parent={container} key={group.heading} group={group} application={this.props.application} />
    ));

    return (
      <div className="execution-groups-section">
        <div className={className}>
          {!hasGroups && (
            <div className="text-center">
              <h4>No executions match the filters you've selected.</h4>
            </div>
          )}
          <div className="execution-groups all-execution-groups" ref={this.setContainer}>
            <BannerContainer app={this.props.application} />
            {container && executionGroups}
          </div>
        </div>
      </div>
    );
  }
}
