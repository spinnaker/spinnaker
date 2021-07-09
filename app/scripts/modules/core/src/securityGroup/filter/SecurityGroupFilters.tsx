import { useOnStateChanged } from '@uirouter/react';
import { chain, compact, map, uniq } from 'lodash';
import React from 'react';

import { Application } from '../../application';
import { FilterSearch } from '../../cluster/filter/FilterSearch';
import { FilterSection } from '../../cluster/filter/FilterSection';
import { ISecurityGroup } from '../../domain';
import { digestDependentFilters, ISortFilter } from '../../filterModel';
import { FilterCheckbox } from '../../filterModel/FilterCheckBox';
import { useDataSource, useObservable } from '../../presentation';
import { SecurityGroupState } from '../../state';

export interface ISecurityGroupFiltersProps {
  app: Application;
}

interface ISecurityGroupHeaders {
  account: string[];
  detail: string[];
  providerType: string[];
  region: string[];
  stack: string[];
}

type ISecurityGroupFilters = 'account' | 'detail' | 'providerType' | 'region' | 'stack';

interface IPoolItem {
  providerType: string;
  account: string;
  region: string;
}

const poolValueCoordinates = [
  { filterField: 'providerType', on: 'securityGroup', localField: 'provider' },
  { filterField: 'account', on: 'securityGroup', localField: 'account' },
  { filterField: 'region', on: 'securityGroup', localField: 'region' },
];

const poolBuilder = (securityGroups: any[]): IPoolItem[] => {
  const pool = securityGroups.map((sg) => {
    const poolUnit = chain(poolValueCoordinates)
      .filter({ on: 'securityGroup' })
      .reduce((poolUnitTemplate: any, coordinate) => {
        poolUnitTemplate[coordinate.filterField] = sg[coordinate.localField];
        return poolUnitTemplate;
      }, {})
      .value();

    return poolUnit;
  });

  return pool;
};

export const SecurityGroupFilters = ({ app }: ISecurityGroupFiltersProps) => {
  const { securityGroups } = app;
  const { data: securityGroupData, loaded: securityGroupsLoaded } = useDataSource<ISecurityGroup[]>(securityGroups);

  const [tags, setTags] = React.useState(SecurityGroupState.filterModel.asFilterModel.tags);
  const [sortFilter, setSortFilter] = React.useState<ISortFilter>(
    SecurityGroupState.filterModel.asFilterModel.sortFilter,
  );

  const getHeadingsForOption = (option: string): string[] =>
    compact(uniq(map(securityGroupData, option) as string[])).sort();
  const [headings, setHeadings] = React.useState<ISecurityGroupHeaders>({
    account: [],
    detail: ['(none)'].concat(getHeadingsForOption('detail')),
    providerType: getHeadingsForOption('provider'),
    region: [],
    stack: ['(none)'].concat(getHeadingsForOption('stack')),
  });

  useObservable(SecurityGroupState.filterService.groupsUpdatedStream, () => {
    setTags(SecurityGroupState.filterModel.asFilterModel.tags);
  });

  useOnStateChanged(() => {
    SecurityGroupState.filterModel.asFilterModel.activate();
    SecurityGroupState.filterService.updateSecurityGroups(app);
  });

  const updateSecurityGroups = (applyParamsToUrl = true): void => {
    const { account, region } = digestDependentFilters({
      sortFilter: SecurityGroupState.filterModel.asFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region'],
      pool: poolBuilder(securityGroupData),
    });

    setHeadings({
      account,
      detail: ['(none)'].concat(getHeadingsForOption('detail')),
      providerType: getHeadingsForOption('provider'),
      region,
      stack: ['(none)'].concat(getHeadingsForOption('stack')),
    });

    if (applyParamsToUrl) {
      SecurityGroupState.filterModel.asFilterModel.applyParamsToUrl();
    }
  };

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const searchTerm = event.target.value;
    const newSortFilter = {
      ...sortFilter,
      filter: searchTerm,
    };
    setSortFilter(newSortFilter);
    SecurityGroupState.filterModel.asFilterModel.sortFilter = newSortFilter;
    updateSecurityGroups();
  };

  const handleFilterChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const value = target.type === 'checkbox' ? target.checked : target.value;
    const [header, filter] = target.name.split('.');

    const newSortFilter = {
      ...sortFilter,
      [header]: {
        ...sortFilter[header as ISecurityGroupFilters],
        [filter]: Boolean(value),
      },
    };

    setSortFilter(newSortFilter);
    SecurityGroupState.filterModel.asFilterModel.sortFilter = newSortFilter;
    updateSecurityGroups();
  };

  React.useEffect(() => {
    if (securityGroupsLoaded) {
      updateSecurityGroups();
    }
  }, [securityGroupsLoaded, tags.length]);

  return (
    <div className="insight-filter-content">
      <div className="heading">
        <FilterSearch
          helpKey="securityGroup.search"
          value={sortFilter.filter}
          onBlur={handleSearchChange}
          onSearchChange={handleSearchChange}
        />
      </div>
      {securityGroupsLoaded && (
        <div className="content">
          {headings.providerType.length > 1 && (
            <FilterSection key="filter-provider" heading="Provider" expanded={true}>
              {headings.providerType.map((heading) => (
                <FilterCheckbox
                  heading={heading}
                  isCloudProvider={true}
                  key={heading}
                  sortFilterType={sortFilter.providerType}
                  onChangeEvent={handleFilterChange}
                  name={`providerType.${heading}`}
                />
              ))}
            </FilterSection>
          )}
          <FilterSection key="filter-account" heading="Account" expanded={true}>
            {headings.account.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.account}
                onChangeEvent={handleFilterChange}
                name={`account.${heading}`}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-region" heading="Region" expanded={true}>
            {headings.region.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.region || {}}
                onChangeEvent={handleFilterChange}
                name={`region.${heading}`}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-stack" heading="Stack" expanded={true}>
            {headings.stack.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.stack}
                onChangeEvent={handleFilterChange}
                name={`stack.${heading}`}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-detail" heading="Detail" expanded={true}>
            {headings.detail.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.detail}
                onChangeEvent={handleFilterChange}
                name={`detail.${heading}`}
              />
            ))}
          </FilterSection>
        </div>
      )}
    </div>
  );
};
