import { Debounce } from 'lodash-decorators';
import React from 'react';
import { Subscription } from 'rxjs';

import { CreateLoadBalancerButton } from './CreateLoadBalancerButton';
import { LoadBalancerPod } from './LoadBalancerPod';
import { Application } from '../application/application.model';
import { BannerContainer } from '../banner';
import { ILoadBalancerGroup } from '../domain';
import { FilterTags, IFilterTag } from '../filterModel/FilterTags';
import { ISortFilter } from '../filterModel/IFilterModel';
import { HelpField } from '../help';
import { ReactInjector } from '../reactShims';
import { LoadBalancerState } from '../state';
import { Spinner } from '../widgets/spinners/Spinner';

export interface ILoadBalancersProps {
  app: Application;
}

export interface ILoadBalancersState {
  initialized: boolean;
  groups: ILoadBalancerGroup[];
  tags: IFilterTag[];
  showServerGroups: boolean;
  showInstances: boolean;
}

export class LoadBalancers extends React.Component<ILoadBalancersProps, ILoadBalancersState> {
  private groupsUpdatedListener: Subscription;
  private loadBalancersRefreshUnsubscribe: () => any;

  constructor(props: ILoadBalancersProps) {
    super(props);
    const { $stateParams } = ReactInjector;
    this.state = {
      initialized: false,
      groups: [],
      tags: [],
      showServerGroups: !$stateParams.hideServerGroups || true,
      showInstances: $stateParams.showInstances || false,
    };
  }

  public componentDidMount(): void {
    const { app } = this.props;

    this.groupsUpdatedListener = LoadBalancerState.filterService.groupsUpdatedStream.subscribe(() =>
      this.groupsUpdated(),
    );
    LoadBalancerState.filterModel.asFilterModel.activate();
    this.loadBalancersRefreshUnsubscribe = app
      .getDataSource('loadBalancers')
      .onRefresh(null, () => this.updateLoadBalancerGroups());
    app.setActiveState(app.loadBalancers);
    this.updateLoadBalancerGroups();
  }

  public componentWillUnmount(): void {
    this.groupsUpdatedListener.unsubscribe();
    this.loadBalancersRefreshUnsubscribe();
  }

  private groupsUpdated(): void {
    this.setState({
      groups: LoadBalancerState.filterModel.asFilterModel.groups,
      tags: LoadBalancerState.filterModel.asFilterModel.tags,
    });
  }

  @Debounce(200)
  private updateLoadBalancerGroups(): void {
    LoadBalancerState.filterModel.asFilterModel.applyParamsToUrl();
    LoadBalancerState.filterService.updateLoadBalancerGroups(this.props.app);
    this.groupsUpdated();

    if (this.props.app.getDataSource('loadBalancers').loaded) {
      this.setState({ initialized: true });
    }
  }

  private clearFilters = (): void => {
    LoadBalancerState.filterService.clearFilters();
    this.updateLoadBalancerGroups();
  };

  private updateUIState(state: ILoadBalancersState): void {
    const params: any = {
      hideServerGroups: undefined,
      showInstances: undefined,
    };
    if (!state.showServerGroups) {
      params.hideServerGroups = true;
    }
    if (state.showInstances) {
      params.showInstances = true;
    }
    ReactInjector.$state.go('.', params);
  }

  private handleInputChange = (event: any): void => {
    const target = event.target;
    const value = target.type === 'checkbox' ? target.checked : target.value;
    const name: keyof ISortFilter = target.name;

    (LoadBalancerState.filterModel.asFilterModel.sortFilter[name] as any) = value;

    const state: any = {}; // Use any type since we can't infer the property name
    state[name] = value;
    this.updateUIState(state);
    this.setState(state);
  };

  private tagCleared = (): void => {
    this.updateLoadBalancerGroups();
  };

  public render(): React.ReactElement<LoadBalancers> {
    const groupings = this.state.initialized ? (
      <div>
        {this.state.groups.map((group) => (
          <div key={group.heading} className="rollup">
            {group.subgroups &&
              group.subgroups.map((subgroup) => (
                <LoadBalancerPod
                  key={subgroup.heading}
                  grouping={subgroup}
                  application={this.props.app}
                  parentHeading={group.heading}
                  showServerGroups={this.state.showServerGroups}
                  showInstances={this.state.showInstances}
                />
              ))}
          </div>
        ))}
        {this.state.groups.length === 0 && (
          <div>
            <h4 className="text-center">No load balancers match the filters you've selected.</h4>
          </div>
        )}
      </div>
    ) : (
      <div>
        <Spinner size="medium" />
      </div>
    );

    return (
      <div className="main-content load-balancers">
        <div className="header row header-clusters">
          <div className="col-lg-8 col-md-10">
            <div className="form-inline clearfix filters">
              <div className="form-group">
                <label className="checkbox"> Show </label>
                <div className="checkbox">
                  <label>
                    {' '}
                    <input
                      type="checkbox"
                      name="showServerGroups"
                      checked={this.state.showServerGroups}
                      onChange={this.handleInputChange}
                    />{' '}
                    Server Groups <HelpField id="loadBalancers.filter.serverGroups" />
                  </label>
                </div>
                <div className="checkbox">
                  <label>
                    {' '}
                    <input
                      type="checkbox"
                      name="showInstances"
                      checked={this.state.showInstances}
                      onChange={this.handleInputChange}
                    />{' '}
                    Instances{' '}
                    <HelpField
                      key="loadBalancers.filter.instances"
                      placement="right"
                      id="loadBalancers.filter.instances"
                    />
                  </label>
                </div>
              </div>
            </div>
          </div>
          <div className="col-lg-4 col-md-2">
            <div className="form-inline clearfix filters" />
            <div className="application-actions">
              <CreateLoadBalancerButton app={this.props.app} />
            </div>
          </div>
          <FilterTags tags={this.state.tags} tagCleared={this.tagCleared} clearFilters={this.clearFilters} />
        </div>

        <div className="content">
          <BannerContainer app={this.props.app} />
          {groupings}
        </div>
      </div>
    );
  }
}
