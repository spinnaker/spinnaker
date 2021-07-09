import { StateDeclaration, useOnStateChanged } from '@uirouter/react';
import { compact, map, uniq } from 'lodash';
import React from 'react';

import { FilterSearch } from './FilterSearch';
import { FilterSection } from './FilterSection';
import LabelFilter from './LabelFilter';
import { Application } from '../../application';
import { poolBuilder } from './clusterDependentFilterHelper.service';
import { IServerGroup } from '../../domain';
import { digestDependentFilters, FilterCheckbox, ISortFilter } from '../../filterModel';
import {
  buildLabelsMap,
  ILabelFilter,
  labelFiltersToTrueKeyObject,
  trueKeyObjectToLabelFilters,
} from './labelFilterUtils';
import { robotToHuman, useDataSource, useObservable } from '../../presentation';
import { ClusterState } from '../../state';

export interface IClusterFiltersProps {
  app: Application;
}

interface IClusterHeaders {
  account: string[];
  availabilityZone: string[];
  category: string[];
  detail: string[];
  instanceType: string[];
  providerType: string[];
  region: string[];
  stack: string[];
}

export const ClusterFilters = ({ app }: IClusterFiltersProps) => {
  const { serverGroups } = app;
  const { data: serverGroupData, loaded: clustersLoaded } = useDataSource<IServerGroup[]>(serverGroups);

  const [tags, setTags] = React.useState(ClusterState.filterModel.asFilterModel.tags);
  const [sortFilter, setSortFilter] = React.useState<ISortFilter>(ClusterState.filterModel.asFilterModel.sortFilter);
  const [labelFilters, setLabelFilters] = React.useState<ILabelFilter[]>(
    trueKeyObjectToLabelFilters(sortFilter.labels),
  );

  const labelsMap = React.useMemo(() => buildLabelsMap(serverGroupData), [serverGroupData.length]);
  const showLabelFilter = Object.keys(labelsMap).length > 0;

  const getHeadingsForOption = (option: string): string[] =>
    compact(uniq(map(serverGroupData, option) as string[])).sort();
  const [headings, setHeadings] = React.useState<IClusterHeaders>({
    account: [],
    availabilityZone: [],
    category: getHeadingsForOption('category'),
    detail: ['(none)'].concat(getHeadingsForOption('detail')),
    instanceType: [],
    providerType: [],
    region: [],
    stack: ['(none)'].concat(getHeadingsForOption('stack')),
  });

  useObservable(ClusterState.filterService.groupsUpdatedStream, () => {
    setTags(ClusterState.filterModel.asFilterModel.tags);
    setSortFilter(ClusterState.filterModel.asFilterModel.sortFilter);
    setLabelFilters(trueKeyObjectToLabelFilters(ClusterState.filterModel.asFilterModel.sortFilter.labels));
  });

  useOnStateChanged((state: StateDeclaration) => {
    if (state.name.includes('clusters')) {
      ClusterState.filterModel.asFilterModel.activate();
    }
  });

  const updateClusterGroups = (applyParamsToUrl = true) => {
    const { providerType, instanceType, account, availabilityZone, region } = digestDependentFilters({
      sortFilter: ClusterState.filterModel.asFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone', 'instanceType'],
      pool: poolBuilder(serverGroupData),
    });

    setHeadings({
      account,
      availabilityZone,
      category: getHeadingsForOption('category'),
      detail: ['(none)'].concat(getHeadingsForOption('detail')),
      instanceType,
      providerType,
      region,
      stack: ['(none)'].concat(getHeadingsForOption('stack')),
    });

    if (applyParamsToUrl) {
      ClusterState.filterModel.asFilterModel.applyParamsToUrl();
    }
    ClusterState.filterService.updateClusterGroups(app);
  };

  const handleLabelFiltersChange = (filters: ILabelFilter[]): void => {
    const newSortFilter = {
      ...sortFilter,
      labels: labelFiltersToTrueKeyObject(filters),
    };

    setLabelFilters(filters);
    setSortFilter(newSortFilter);
    ClusterState.filterModel.asFilterModel.sortFilter = newSortFilter;
    updateClusterGroups();
  };

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const searchTerm = event.target.value;
    const newSortFilter = {
      ...sortFilter,
      filter: searchTerm,
    };
    setSortFilter(newSortFilter);
    ClusterState.filterModel.asFilterModel.sortFilter = newSortFilter;
    updateClusterGroups();
  };

  const handleStatusChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const value = target.type === 'checkbox' ? target.checked : target.value;
    const name = target.name;

    const newSortFilter = {
      ...sortFilter,
      status: {
        ...sortFilter.status,
        [name]: Boolean(value),
      },
    };
    setSortFilter(newSortFilter);
    ClusterState.filterModel.asFilterModel.sortFilter = newSortFilter;
    updateClusterGroups();
  };

  const handleMinInstanceChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const numInstances = event.target.value;
    const min = numInstances ? parseInt(numInstances, 10) : undefined;
    const newSortFilter = {
      ...sortFilter,
      minInstances: min,
    };
    setSortFilter(newSortFilter);
    ClusterState.filterModel.asFilterModel.sortFilter = newSortFilter;
    updateClusterGroups();
  };

  const handleMaxInstanceChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const numInstances = event.target.value;
    const max = numInstances ? parseInt(numInstances, 10) : undefined;
    const newSortFilter = {
      ...sortFilter,
      maxInstances: max,
    };
    setSortFilter(newSortFilter);
    ClusterState.filterModel.asFilterModel.sortFilter = newSortFilter;
    updateClusterGroups();
  };

  React.useEffect(() => {
    if (clustersLoaded) {
      updateClusterGroups();
    }
  }, [clustersLoaded, tags.length]);

  return (
    <div className="insight-filter-content">
      <div className="heading">
        <FilterSearch
          helpKey="cluster.search"
          value={sortFilter.filter}
          onBlur={handleSearchChange}
          onSearchChange={handleSearchChange}
        />
      </div>
      {clustersLoaded && (
        <div className="content">
          {headings.providerType.length > 1 && (
            <FilterSection key="filter-provider" heading="Provider" expanded={true}>
              {headings.providerType.map((heading) => (
                <FilterCheckbox
                  heading={heading}
                  isCloudProvider={true}
                  key={heading}
                  sortFilterType={sortFilter.providerType}
                  onChange={updateClusterGroups}
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
                onChange={updateClusterGroups}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-region" heading="Region" expanded={true}>
            {headings.region.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.region}
                onChange={updateClusterGroups}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-category" heading="Category" expanded={true}>
            {headings.category.map((heading) => (
              <FilterCheckbox
                heading={robotToHuman(heading)}
                key={heading}
                sortFilterType={sortFilter.category}
                onChange={updateClusterGroups}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-stack" heading="Stack" expanded={true}>
            {headings.stack.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.stack}
                onChange={updateClusterGroups}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-detail" heading="Detail" expanded={true}>
            {headings.detail.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.detail}
                onChange={updateClusterGroups}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-status" heading="Status" expanded={true}>
            <div className="form">
              {['Up', 'Down', 'Disabled', 'Starting', 'OutOfService', 'Unknown'].map((status) => (
                <div className="checkbox" key={status}>
                  <label>
                    <input
                      key={status}
                      type="checkbox"
                      checked={Boolean(sortFilter.status && sortFilter.status[status])}
                      onChange={handleStatusChange}
                      name={status}
                    />
                    {robotToHuman(status)}
                  </label>
                </div>
              ))}
            </div>
          </FilterSection>
          <FilterSection key="filter-az" heading="Availability Zones" expanded={true}>
            {headings.availabilityZone.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.availabilityZone}
                onChange={updateClusterGroups}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-instance-types" heading="Instance Types" expanded={true}>
            {headings.instanceType.map((heading) => (
              <FilterCheckbox
                heading={heading}
                key={heading}
                sortFilterType={sortFilter.instanceType}
                onChange={updateClusterGroups}
              />
            ))}
          </FilterSection>
          <FilterSection key="filter-instance-count" heading="Instance Count" expanded={true}>
            <div className="form-inline">
              <div className="form-group">
                {'Min:  '}
                <input
                  type="number"
                  className="form-control input-sm"
                  value={sortFilter.minInstances || ''}
                  onChange={handleMinInstanceChange}
                />
              </div>
              <div className="form-group">
                {'Max:  '}
                <input
                  type="number"
                  className="form-control input-sm"
                  value={sortFilter.maxInstances || ''}
                  onChange={handleMaxInstanceChange}
                />
              </div>
            </div>
          </FilterSection>
          {showLabelFilter && (
            <FilterSection key="filter-label" heading="Labels" expanded={true}>
              <LabelFilter
                labelsMap={labelsMap}
                labelFilters={labelFilters}
                updateLabelFilters={handleLabelFiltersChange}
              />
            </FilterSection>
          )}
        </div>
      )}
    </div>
  );
};
