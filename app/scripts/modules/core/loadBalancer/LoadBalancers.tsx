import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { Debounce } from 'lodash-decorators';
import { Subscription } from 'rxjs';

import { $stateParams, $state } from 'core/uirouter';
import { Application } from 'core/application/application.model';
import { FilterTags, IFilterTag } from 'core/filterModel/FilterTags';
import { HelpField } from 'core/help/HelpField';
import { ILoadBalancer, ILoadBalancerGroup } from 'core/domain';
import { LoadBalancerPod } from './LoadBalancerPod';
import { StickyContainer } from 'core/utils/stickyHeader/StickyContainer';
import { Spinner } from 'core/widgets/Spinner';
import { Tooltip } from 'core/presentation/Tooltip';

import { cloudProviderRegistry } from 'core/cloudProvider/cloudProvider.registry';
import { loadBalancerFilterModel } from './filter/loadBalancerFilter.model';
import { loadBalancerFilterService } from './filter/loadBalancer.filter.service';
import { modalService } from 'core/modal.service';
import { providerSelectionService } from 'core/cloudProvider/providerSelection/providerSelection.service';

interface IProps {
  app: Application;
}

interface IState {
  initialized: boolean;
  groups: ILoadBalancerGroup[];
  tags: IFilterTag[];
  showServerGroups: boolean;
  showInstances: boolean;
}

@autoBindMethods
export class LoadBalancers extends React.Component<IProps, IState> {
  private groupsUpdatedListener: Subscription;
  private loadBalancersRefreshUnsubscribe: () => any;

  constructor(props: IProps) {
    super(props);
    this.state = {
      initialized: false,
      groups: [],
      tags: [],
      showServerGroups: !$stateParams.hideServerGroups || true,
      showInstances: $stateParams.showInstances || false
    };

    this.groupsUpdatedListener = loadBalancerFilterService.groupsUpdatedStream.subscribe(() => this.groupsUpdated());
    loadBalancerFilterModel.asFilterModel.activate();

    this.loadBalancersRefreshUnsubscribe = props.app.getDataSource('loadBalancers').onRefresh(null, () => this.updateLoadBalancerGroups());

    props.app.activeState = props.app.loadBalancers;
  }

  private groupsUpdated(): void {
    this.setState({
      groups: loadBalancerFilterModel.asFilterModel.groups,
      tags: loadBalancerFilterModel.asFilterModel.tags,
    })
  }

  @Debounce(200)
  private updateLoadBalancerGroups(): void {
    loadBalancerFilterModel.asFilterModel.applyParamsToUrl();
    loadBalancerFilterService.updateLoadBalancerGroups(this.props.app);
    this.groupsUpdated();

    if (this.props.app.getDataSource('loadBalancers').loaded) {
      this.setState({ initialized: true });
    }
  }

  private clearFilters(): void {
    loadBalancerFilterService.clearFilters();
    this.updateLoadBalancerGroups();
  }

  private createLoadBalancer(): void {
    providerSelectionService.selectProvider(this.props.app, 'loadBalancer').then((selectedProvider) => {
      const provider = cloudProviderRegistry.getValue(selectedProvider, 'loadBalancer');
      modalService.open({
        templateUrl: provider.createLoadBalancerTemplateUrl,
        controller: `${provider.createLoadBalancerController} as ctrl`,
        size: 'lg',
        resolve: {
          application: () => this.props.app,
          loadBalancer: (): ILoadBalancer => null,
          isNew: () => true,
          forPipelineConfig: () => false
        }
      });
    });
  };

  private updateUIState(state: IState): void {
    const params: any = {};
    if (!state.showServerGroups) {
      params.hideServerGroups = true;
    }
    if (state.showInstances) {
      params.showInstances = true;
    }
    $state.go('.', params);
  }

  private handleInputChange(event: any): void {
    const target = event.target;
    const value = target.type === 'checkbox' ? target.checked : target.value;
    const name = target.name;

    loadBalancerFilterModel.asFilterModel.sortFilter[name] = value;

    const state: any = {}; // Use any type since we can't infer the property name
    state[name] = value;
    this.updateUIState(state);
    this.setState(state);
  }

  public componentDidMount(): void {
    this.updateLoadBalancerGroups();
  }

  public componentWillUnmount(): void {
    this.props.app.activeState = this.props.app;
    this.groupsUpdatedListener.unsubscribe();
    this.loadBalancersRefreshUnsubscribe();
  }

  private tagCleared(): void {
    this.updateLoadBalancerGroups();
  }

  public render(): React.ReactElement<LoadBalancers> {
    const groupings = this.state.initialized ? (
      <div>
        { this.state.groups.map((group) => (
          <div key={group.heading} className="rollup">
            { group.subgroups && group.subgroups.map((subgroup) => (
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
        {this.state.groups.length === 0 && <div><h4 className="text-center">No load balancers match the filters you've selected.</h4></div>}
      </div>
    ) : (
      <div>
        <h3><Spinner radius={30} width={8} length={16}/></h3>
      </div>
    );

    return (
      <div className="main-content load-balancers">
        <div className="header row header-clusters">
          <div className="col-lg-8 col-md-10">
            <h3>
              Load Balancers
            </h3>
            <div className="form-inline clearfix filters">
              <div className="form-group">
                <label className="checkbox"> Show </label>
                <div className="checkbox">
                  <label> <input type="checkbox" name="showServerGroups" checked={this.state.showServerGroups} onChange={this.handleInputChange} /> Server Groups <HelpField id="loadBalancers.filter.serverGroups"/></label>
                </div>
                <div className="checkbox">
                  <label> <input type="checkbox" name="showInstances" checked={this.state.showInstances} onChange={this.handleInputChange} /> Instances <HelpField placement="right" id="loadBalancers.filter.instances"/></label>
                </div>
              </div>
            </div>

          </div>
          <div className="col-lg-4 col-md-2">
            <div className="form-inline clearfix filters"/>
            <div className="application-actions">
              <button className="btn btn-sm btn-default" onClick={this.createLoadBalancer}>
                <span className="glyphicon glyphicon-plus-sign visible-lg-inline"/>
                <Tooltip value="Create Load Balancer">
                  <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline"/>
                </Tooltip>
                <span className="visible-lg-inline"> Create Load Balancer</span>
              </button>
            </div>
          </div>
          <FilterTags tags={this.state.tags} tagCleared={this.tagCleared} clearFilters={this.clearFilters}/>
        </div>
        <StickyContainer className="content" data-scroll-id="loadbalancers-content">
          {groupings}
        </StickyContainer>
      </div>
    );
  }
}
