import React, { useEffect, useState } from 'react';

import { AccountTag } from '../../account';
import type { Application } from '../../application';
import type { ApplicationDataSource } from '../../application/service/applicationDataSource';
import type { IClusterSummary } from '../../domain';
import { ReactSelectInput } from '../../presentation';
import { ClusterState } from '../../state';

import './onDemandClusterPicker.component.less';

export interface IOnDemandClusterOption {
  account: string;
  label: string;
  name: string;
  value: string;
}

export interface IOnDemandClusterPickerProps {
  application: Application;
}

type OnDemandServerGroupsDataSource = ApplicationDataSource<unknown[]> & {
  clusters?: IClusterSummary[];
  fetchOnDemand?: boolean;
};

export function makeClusterFilterKey(cluster: Pick<IClusterSummary, 'account' | 'name'>): string {
  return `${cluster.account}:${cluster.name}`;
}

export function getAvailableClusters(
  clusters: IClusterSummary[] = [],
  selectedClusters: Record<string, unknown> = {},
): IOnDemandClusterOption[] {
  return (clusters || [])
    .filter((cluster) => !selectedClusters?.[makeClusterFilterKey(cluster)])
    .map((cluster) => ({
      account: cluster.account,
      label: cluster.name,
      name: cluster.name,
      value: makeClusterFilterKey(cluster),
    }));
}

export function filterClusterOptions(
  options: IOnDemandClusterOption[] = [],
  filter = '',
  limit = 50,
): IOnDemandClusterOption[] {
  const normalizedFilter = filter.toLocaleLowerCase();
  return (options || [])
    .filter(({ account, name }) => `${account} ${name}`.toLocaleLowerCase().includes(normalizedFilter))
    .slice(0, limit);
}

export function OnDemandClusterPicker({ application }: IOnDemandClusterPickerProps): JSX.Element {
  const serverGroups = application.getDataSource('serverGroups') as OnDemandServerGroupsDataSource;
  const readClusters = () => {
    const clusters = Array.isArray(serverGroups.clusters) ? serverGroups.clusters : [];
    const selectedClusters = ClusterState.filterModel.asFilterModel.sortFilter.clusters || {};
    return {
      availableClusters: getAvailableClusters(clusters, selectedClusters),
      totalClusterCount: clusters.length,
    };
  };
  const [clusterState, setClusterState] = useState(readClusters);

  useEffect(() => {
    const updateClusters = () => setClusterState(readClusters());
    updateClusters();
    return serverGroups.onRefresh(null, updateClusters);
  }, [application]);

  const selectCluster = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const clusterKey = event?.target?.value;
    if (!clusterKey) {
      return;
    }

    const sortFilter = ClusterState.filterModel.asFilterModel.sortFilter;
    sortFilter.clusters = sortFilter.clusters || {};
    sortFilter.clusters[clusterKey] = true;
    ClusterState.filterModel.asFilterModel.applyParamsToUrl();
    setClusterState(readClusters());
    serverGroups.refresh(true);
  };

  const renderOption = (option: IOnDemandClusterOption): JSX.Element => (
    <span>
      <AccountTag account={option.account} /> <span>{option.name}</span>
    </span>
  );

  return (
    <div className="on-demand-cluster-picker">
      <h4>{clusterState.totalClusterCount} clusters found in this application</h4>
      <p>
        <strong>Not all clusters are shown.</strong> Select or enter a cluster name below to view:
      </p>
      <ReactSelectInput
        mode="PLAIN"
        name="cluster"
        value={null}
        options={clusterState.availableClusters}
        filterOptions={(options, filter) => filterClusterOptions(options as IOnDemandClusterOption[], filter)}
        optionRenderer={renderOption}
        valueRenderer={renderOption}
        onChange={selectCluster}
        placeholder="Enter cluster name here"
      />
    </div>
  );
}
