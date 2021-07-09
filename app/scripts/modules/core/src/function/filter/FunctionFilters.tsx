import { chain, compact, debounce, map, uniq } from 'lodash';
import { $rootScope } from 'ngimport';
import React from 'react';
import { Subscription } from 'rxjs';

import { Application } from '../../application';
import { FilterSearch } from '../../cluster/filter/FilterSearch';
import { FilterSection } from '../../cluster/filter/FilterSection';
import { digestDependentFilters, FilterCheckbox, ISortFilter } from '../../filterModel';
import { FunctionState } from '../../state';

const poolValueCoordinates = [
  { filterField: 'providerType', on: 'function', localField: 'type' },
  { filterField: 'account', on: 'function', localField: 'account' },
  { filterField: 'region', on: 'function', localField: 'region' },
];

function poolBuilder(functions: any[]) {
  const pool = chain(functions)
    .map((fn) => {
      const poolUnits = chain(poolValueCoordinates)
        .filter({ on: 'function' })
        .reduce((acc, coordinate) => {
          acc[coordinate.filterField] = fn[coordinate.localField];
          return acc;
        }, {} as any)
        .value();
      return poolUnits;
    })
    .flatten()
    .value();
  return pool;
}

export interface IFunctionFiltersProps {
  app: Application;
}

export interface IFunctionFiltersState {
  sortFilter: ISortFilter;
  tags: any[];
  providerTypeHeadings: string[];
  accountHeadings: string[];
  regionHeadings: string[];
}

export class FunctionFilters extends React.Component<IFunctionFiltersProps, IFunctionFiltersState> {
  private debouncedUpdateFunctionGroups: () => void;
  private groupsUpdatedSubscription: Subscription;
  private functionsRefreshUnsubscribe: () => void;
  private locationChangeUnsubscribe: () => void;

  constructor(props: IFunctionFiltersProps) {
    super(props);
    this.state = {
      sortFilter: FunctionState.filterModel.asFilterModel.sortFilter,
      tags: FunctionState.filterModel.asFilterModel.tags,
      providerTypeHeadings: [],
      accountHeadings: [],
      regionHeadings: [],
    };

    this.debouncedUpdateFunctionGroups = debounce(this.updateFunctionGroups, 300);
  }

  public componentDidMount(): void {
    const { app } = this.props;

    this.groupsUpdatedSubscription = FunctionState.filterService.groupsUpdatedStream.subscribe(() => {
      this.setState({ tags: FunctionState.filterModel.asFilterModel.tags });
    });

    if (app.functions && app.functions.loaded) {
      this.updateFunctionGroups();
    }

    this.functionsRefreshUnsubscribe = app.functions.onRefresh(null, () => this.updateFunctionGroups());

    this.locationChangeUnsubscribe = $rootScope.$on('$locationChangeSuccess', () => {
      FunctionState.filterModel.asFilterModel.activate();
      FunctionState.filterService.updateFunctionGroups(app);
    });
  }

  public componentWillUnmount(): void {
    this.groupsUpdatedSubscription.unsubscribe();
    this.functionsRefreshUnsubscribe();
    this.locationChangeUnsubscribe();
  }

  public updateFunctionGroups = (applyParamsToUrl = true): void => {
    const { app } = this.props;

    if (applyParamsToUrl) {
      FunctionState.filterModel.asFilterModel.applyParamsToUrl();
    }
    FunctionState.filterService.updateFunctionGroups(app);

    const { region, account } = digestDependentFilters({
      sortFilter: FunctionState.filterModel.asFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region'],
      pool: poolBuilder(app.functions.data),
    });

    this.setState({
      accountHeadings: account,
      regionHeadings: region,
      providerTypeHeadings: this.getHeadingsForOption('type'),
    });
  };

  private getHeadingsForOption = (option: string): string[] => {
    return compact(uniq(map(this.props.app.functions.data, option) as string[])).sort();
  };

  private handleSearchBlur = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    this.state.sortFilter.filter = target.value;
    this.updateFunctionGroups();
  };

  private handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    this.state.sortFilter.filter = target.value;
    this.setState({ sortFilter: this.state.sortFilter });
    this.debouncedUpdateFunctionGroups();
  };

  public render() {
    const fuctionsLoaded = this.props.app.functions.loaded;
    const { accountHeadings, providerTypeHeadings, regionHeadings, sortFilter } = this.state;

    return (
      <div className="insight-filter-content">
        <div className="heading">
          <FilterSearch
            helpKey="functions.search"
            value={sortFilter.filter}
            onBlur={this.handleSearchBlur}
            onSearchChange={this.handleSearchChange}
          />
        </div>
        {fuctionsLoaded && (
          <div className="content">
            {providerTypeHeadings.length > 1 && (
              <FilterSection heading="Provider" expanded={true}>
                {providerTypeHeadings.map((heading) => (
                  <FilterCheckbox
                    heading={heading}
                    isCloudProvider={true}
                    key={heading}
                    sortFilterType={sortFilter.providerType}
                    onChange={this.updateFunctionGroups}
                  />
                ))}
              </FilterSection>
            )}

            <FilterSection heading="Account" expanded={true}>
              {accountHeadings.map((heading) => (
                <FilterCheckbox
                  heading={heading}
                  key={heading}
                  sortFilterType={sortFilter.account}
                  onChange={this.updateFunctionGroups}
                />
              ))}
            </FilterSection>

            <FilterSection heading="Region" expanded={true}>
              {regionHeadings.map((heading) => (
                <FilterCheckbox
                  heading={heading}
                  key={heading}
                  sortFilterType={sortFilter.region}
                  onChange={this.updateFunctionGroups}
                />
              ))}
            </FilterSection>
          </div>
        )}
      </div>
    );
  }
}
