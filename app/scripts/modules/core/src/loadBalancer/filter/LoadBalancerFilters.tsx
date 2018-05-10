import * as React from 'react';
import { chain, cloneDeep, compact, debounce, uniq, map } from 'lodash';
import { $rootScope } from 'ngimport';
import { Subscription } from 'rxjs';

import { Application } from 'core/application';
import { CloudProviderLabel, CloudProviderLogo } from 'core/cloudProvider';
import { FilterCollapse, ISortFilter, digestDependentFilters } from 'core/filterModel';
import { FilterSection } from 'core/cluster/filter/FilterSection';
import { LoadBalancerState } from 'core/state';

const poolValueCoordinates = [
  { filterField: 'providerType', on: 'loadBalancer', localField: 'type' },
  { filterField: 'account', on: 'loadBalancer', localField: 'account' },
  { filterField: 'region', on: 'loadBalancer', localField: 'region' },
  { filterField: 'availabilityZone', on: 'instance', localField: 'zone' },
];

function poolBuilder(loadBalancers: any[]) {
  const pool = chain(loadBalancers)
    .map(lb => {
      const poolUnitTemplate = chain(poolValueCoordinates)
        .filter({ on: 'loadBalancer' })
        .reduce(
          (acc, coordinate) => {
            acc[coordinate.filterField] = lb[coordinate.localField];
            return acc;
          },
          {} as any,
        )
        .value();

      const poolUnits = chain(['instances', 'detachedInstances'])
        .map(instanceStatus => lb[instanceStatus])
        .flatten<any>()
        .map(instance => {
          const poolUnit = cloneDeep(poolUnitTemplate);
          if (!instance) {
            return poolUnit;
          }

          return chain(poolValueCoordinates)
            .filter({ on: 'instance' })
            .reduce((acc, coordinate) => {
              acc[coordinate.filterField] = instance[coordinate.localField];
              return acc;
            }, poolUnit)
            .value();
        })
        .value();

      if (!poolUnits.length) {
        poolUnits.push(poolUnitTemplate);
      }

      return poolUnits;
    })
    .flatten()
    .value();

  return pool;
}

export interface ILoadBalancerFiltersProps {
  app: Application;
}

export interface ILoadBalancerFiltersState {
  sortFilter: ISortFilter;
  tags: any[];
  availabilityZoneHeadings: string[];
  providerTypeHeadings: string[];
  accountHeadings: string[];
  regionHeadings: string[];
  stackHeadings: string[];
  detailHeadings: string[];
}

export class LoadBalancerFilters extends React.Component<ILoadBalancerFiltersProps, ILoadBalancerFiltersState> {
  private debouncedUpdateLoadBalancerGroups: () => void;
  private groupsUpdatedSubscription: Subscription;
  private loadBalancersRefreshUnsubscribe: () => void;
  private locationChangeUnsubscribe: () => void;

  constructor(props: ILoadBalancerFiltersProps) {
    super(props);
    this.state = {
      sortFilter: LoadBalancerState.filterModel.asFilterModel.sortFilter,
      tags: LoadBalancerState.filterModel.asFilterModel.tags,
      availabilityZoneHeadings: [],
      providerTypeHeadings: [],
      accountHeadings: [],
      regionHeadings: [],
      stackHeadings: [],
      detailHeadings: [],
    };

    this.debouncedUpdateLoadBalancerGroups = debounce(this.updateLoadBalancerGroups, 300);
  }

  public componentDidMount(): void {
    const { app } = this.props;

    this.groupsUpdatedSubscription = LoadBalancerState.filterService.groupsUpdatedStream.subscribe(() => {
      this.setState({ tags: LoadBalancerState.filterModel.asFilterModel.tags });
    });

    if (app.loadBalancers && app.loadBalancers.loaded) {
      this.updateLoadBalancerGroups();
    }

    this.loadBalancersRefreshUnsubscribe = app.loadBalancers.onRefresh(null, () => this.updateLoadBalancerGroups());

    this.locationChangeUnsubscribe = $rootScope.$on('$locationChangeSuccess', () => {
      LoadBalancerState.filterModel.asFilterModel.activate();
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);
    });
  }

  public componentWillUnmount(): void {
    this.groupsUpdatedSubscription.unsubscribe();
    this.loadBalancersRefreshUnsubscribe();
    this.locationChangeUnsubscribe();
  }

  public updateLoadBalancerGroups = (applyParamsToUrl = true): void => {
    const { app } = this.props;

    if (applyParamsToUrl) {
      LoadBalancerState.filterModel.asFilterModel.applyParamsToUrl();
    }
    LoadBalancerState.filterService.updateLoadBalancerGroups(app);

    const { availabilityZone, region, account } = digestDependentFilters({
      sortFilter: LoadBalancerState.filterModel.asFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone'],
      pool: poolBuilder(app.loadBalancers.data),
    });

    this.setState({
      accountHeadings: account,
      regionHeadings: region,
      availabilityZoneHeadings: availabilityZone,
      stackHeadings: ['(none)'].concat(this.getHeadingsForOption('stack')),
      detailHeadings: ['(none)'].concat(this.getHeadingsForOption('detail')),
      providerTypeHeadings: this.getHeadingsForOption('type'),
    });
  };

  private getHeadingsForOption = (option: string): string[] => {
    return compact(uniq(map(this.props.app.loadBalancers.data, option) as string[])).sort();
  };

  private clearFilters = (): void => {
    LoadBalancerState.filterService.clearFilters();
    LoadBalancerState.filterModel.asFilterModel.applyParamsToUrl();
    LoadBalancerState.filterService.updateLoadBalancerGroups(this.props.app);
  };

  private handleStatusChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const value = target.type === 'checkbox' ? target.checked : target.value;
    const name = target.name;
    this.state.sortFilter.status[name] = Boolean(value);
    this.updateLoadBalancerGroups();
  };

  private handleSearchBlur = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    this.state.sortFilter.filter = target.value;
    this.updateLoadBalancerGroups();
  };

  private handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    this.state.sortFilter.filter = target.value;
    this.setState({ sortFilter: this.state.sortFilter });
    this.debouncedUpdateLoadBalancerGroups();
  };

  public render() {
    const loadBalancersLoaded = this.props.app.loadBalancers.loaded;
    const {
      accountHeadings,
      providerTypeHeadings,
      regionHeadings,
      stackHeadings,
      detailHeadings,
      availabilityZoneHeadings,
      sortFilter,
      tags,
    } = this.state;

    return (
      <div>
        <FilterCollapse />
        <div className="heading">
          <span
            className="btn btn-default btn-xs"
            style={{ visibility: tags.length > 0 ? 'inherit' : 'hidden' }}
            onClick={this.clearFilters}
          >
            Clear All
          </span>
          <FilterSection heading="Search" expanded={true} helpKey="loadBalancer.search">
            <form className="form-horizontal" role="form">
              <div className="form-group nav-search">
                <input
                  type="search"
                  className="form-control input-sm"
                  value={sortFilter.filter}
                  onBlur={this.handleSearchBlur}
                  onChange={this.handleSearchChange}
                  style={{ width: '85%', display: 'inline-block' }}
                />
              </div>
            </form>
          </FilterSection>
        </div>
        {loadBalancersLoaded && (
          <div className="content">
            {providerTypeHeadings.length > 1 && (
              <FilterSection heading="Provider" expanded={true}>
                {providerTypeHeadings.map(heading => (
                  <FilterCheckbox
                    heading={heading}
                    isCloudProvider={true}
                    key={heading}
                    sortFilterType={sortFilter.providerType}
                    onChange={this.updateLoadBalancerGroups}
                  />
                ))}
              </FilterSection>
            )}

            <FilterSection heading="Account" expanded={true}>
              {accountHeadings.map(heading => (
                <FilterCheckbox
                  heading={heading}
                  key={heading}
                  sortFilterType={sortFilter.account}
                  onChange={this.updateLoadBalancerGroups}
                />
              ))}
            </FilterSection>

            <FilterSection heading="Region" expanded={true}>
              {regionHeadings.map(heading => (
                <FilterCheckbox
                  heading={heading}
                  key={heading}
                  sortFilterType={sortFilter.region}
                  onChange={this.updateLoadBalancerGroups}
                />
              ))}
            </FilterSection>

            <FilterSection heading="Stack" expanded={true}>
              {stackHeadings.map(heading => (
                <FilterCheckbox
                  heading={heading}
                  key={heading}
                  sortFilterType={sortFilter.stack}
                  onChange={this.updateLoadBalancerGroups}
                />
              ))}
            </FilterSection>

            <FilterSection heading="Detail" expanded={true}>
              {detailHeadings.map(heading => (
                <FilterCheckbox
                  heading={heading}
                  key={heading}
                  sortFilterType={sortFilter.detail}
                  onChange={this.updateLoadBalancerGroups}
                />
              ))}
            </FilterSection>

            <FilterSection heading="Instance Status" expanded={true}>
              <div className="form">
                <div className="checkbox">
                  <label>
                    <input
                      type="checkbox"
                      checked={Boolean(sortFilter.status && sortFilter.status.Up)}
                      onChange={this.handleStatusChange}
                      name="Up"
                    />
                    Healthy
                  </label>
                </div>
                <div className="checkbox">
                  <label>
                    <input
                      type="checkbox"
                      checked={Boolean(sortFilter.status && sortFilter.status.Down)}
                      onChange={this.handleStatusChange}
                      name="Down"
                    />
                    Unhealthy
                  </label>
                </div>
                <div className="checkbox">
                  <label>
                    <input
                      type="checkbox"
                      checked={Boolean(sortFilter.status && sortFilter.status.OutOfService)}
                      onChange={this.handleStatusChange}
                      name="OutOfService"
                    />
                    Out of Service
                  </label>
                </div>
              </div>
            </FilterSection>

            <FilterSection heading="Availability Zones" expanded={true}>
              {availabilityZoneHeadings.map(heading => (
                <FilterCheckbox
                  heading={heading}
                  key={heading}
                  sortFilterType={sortFilter.availabilityZone}
                  onChange={this.updateLoadBalancerGroups}
                />
              ))}
            </FilterSection>
          </div>
        )}
      </div>
    );
  }
}

const FilterCheckbox = (props: {
  heading: string;
  sortFilterType: { [key: string]: boolean };
  onChange: () => void;
  isCloudProvider?: boolean;
}): JSX.Element => {
  const { heading, isCloudProvider, onChange, sortFilterType } = props;
  const changeHandler = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const value = target.type === 'checkbox' ? target.checked : target.value;
    sortFilterType[heading] = Boolean(value);
    onChange();
  };
  return (
    <div className="checkbox">
      <label>
        <input type="checkbox" checked={Boolean(sortFilterType[heading])} onChange={changeHandler} />
        {!isCloudProvider ? (
          heading
        ) : (
          <>
            <CloudProviderLogo provider="heading" height="'14px'" width="'14px'" />
            <CloudProviderLabel provider={heading} />
          </>
        )}
      </label>
    </div>
  );
};
