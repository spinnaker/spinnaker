import * as React from 'react';
import { StickyContainer } from 'core/utils/stickyHeader/StickyContainer';
import { Subscription } from 'rxjs/Subscription';

import { Application } from 'core/application/application.model';
import { ExecutionGroup } from './ExecutionGroup';
import { IExecutionGroup } from 'core/domain';
import { executionFilterModel } from 'core/delivery/filter/executionFilter.model';
import { $state } from 'core/uirouter';
import { stateEvents } from 'core/state.events';

import './executionGroups.less';

interface IProps {
  application: Application;
};

interface IState {
  groups: IExecutionGroup[];
  showingDetails: boolean;
};

export class ExecutionGroups extends React.Component<IProps, IState> {
  private applicationRefreshUnsubscribe: () => void;
  private groupsUpdatedSubscription: Subscription;
  private stateChangeSuccessSubscription: Subscription;

  constructor(props: IProps) {
    super(props);

    this.state = {
      groups: executionFilterModel.groups,
      showingDetails: this.showingDetails()
    }
  }

  private showingDetails(): boolean {
    return $state.includes('**.execution');
  }

  public componentDidMount(): void {
    this.applicationRefreshUnsubscribe = this.props.application.executions.onRefresh(null, () => { this.forceUpdate(); });
    this.groupsUpdatedSubscription = executionFilterModel.groupsUpdated.subscribe(() => { this.setState({groups: executionFilterModel.groups}); });
    this.stateChangeSuccessSubscription = stateEvents.stateChangeSuccess.subscribe(() => {
      const detailsShown = this.showingDetails();
      if (detailsShown !== this.state.showingDetails) {
        this.setState({showingDetails: detailsShown});
      }
    });
  }

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
    const hasGroups = this.state.groups && this.state.groups.length > 0;
    const className = `row pipelines executions ${this.state.showingDetails ? 'showing-details' : ''}`;
    const executionGroups = (this.state.groups || []).map((group: IExecutionGroup) => <ExecutionGroup key={group.heading} group={group} application={this.props.application}/>);

    return (
      <div className={className}>
          { !hasGroups && (
            <div className="text-center">
              <h4>No executions match the filters you've selected.</h4>
            </div>
          )}
          <StickyContainer className="execution-groups all-execution-groups">
            { executionGroups }
          </StickyContainer>
      </div>
    );
  }
}
