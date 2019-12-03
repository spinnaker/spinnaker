import React from 'react';
import { chain, compact, debounce, uniq, map } from 'lodash';
import { $rootScope } from 'ngimport';
import { Subscription } from 'rxjs';

import { Application } from 'core/application';
import { CloudProviderLabel, CloudProviderLogo } from 'core/cloudProvider';
import { FilterCollapse, ISortFilter, digestDependentFilters } from 'core/filterModel';
import { FilterSection } from 'core/cluster/filter/FilterSection';
import { FunctionState } from 'core/state';

const poolValueCoordinates = [
  { filterField: 'providerType', on: 'function', localField: 'type' },
  { filterField: 'account', on: 'function', localField: 'account' },
  { filterField: 'region', on: 'function', localField: 'region' },
];

function poolBuilder(functions: any[]) {
  const pool = chain(functions)
    .map(fn => {
      const poolUnits = chain(poolValueCoordinates)
        .filter({ on: 'function' })
        .reduce(
          (acc, coordinate) => {
            acc[coordinate.filterField] = fn[coordinate.localField];
            return acc;
          },
          {} as any,
        )
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

  private clearFilters = (): void => {
    FunctionState.filterService.clearFilters();
    FunctionState.filterModel.asFilterModel.applyParamsToUrl();
    FunctionState.filterService.updateFunctionGroups(this.props.app);
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
    const { accountHeadings, providerTypeHeadings, regionHeadings, sortFilter, tags } = this.state;

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
          <FilterSection heading="Search" expanded={true} helpKey="functions.search">
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
        {fuctionsLoaded && (
          <div className="content">
            {providerTypeHeadings.length > 1 && (
              <FilterSection heading="Provider" expanded={true}>
                {providerTypeHeadings.map(heading => (
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
              {accountHeadings.map(heading => (
                <FilterCheckbox
                  heading={heading}
                  key={heading}
                  sortFilterType={sortFilter.account}
                  onChange={this.updateFunctionGroups}
                />
              ))}
            </FilterSection>

            <FilterSection heading="Region" expanded={true}>
              {regionHeadings.map(heading => (
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
