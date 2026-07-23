import React from 'react';

import { AccountSelectInput } from '../account';
import type { IAccount } from '../account';
import { AccountService } from '../account/AccountService';
import type { Application } from '../application';
import { AppListExtractor } from '../application/listExtractor/AppListExtractor';
import { StageConfigField } from '../pipeline/config/stages/common/stageConfigField/StageConfigField';
import { ChecklistInput } from '../presentation/forms/inputs/ChecklistInput';

export interface IAccountRegionClusterSelectorProps {
  application: Application;
  component: Record<string, any>;
  accounts: IAccount[] | string[];
  clusterField?: string;
  singleRegion?: string;
  showAllRegions?: boolean;
  onAccountUpdate?: (account: string) => void;
  disableRegionSelect?: boolean;
  showClusterSelect?: boolean;
}

export function AccountRegionClusterSelector(props: IAccountRegionClusterSelectorProps) {
  const { application, accounts, component, singleRegion, disableRegionSelect } = props;
  const clusterField = props.clusterField || 'cluster';
  const showClusterSelect = props.showClusterSelect === undefined ? true : props.showClusterSelect;
  const showAllRegions = props.showAllRegions || false;
  const [allRegions, setAllRegions] = React.useState<string[]>([]);
  const [regions, setRegions] = React.useState<string[]>([]);
  const [clusterList, setClusterList] = React.useState<string[]>([]);
  const [clusterIsTextInput, setClusterIsTextInput] = React.useState(false);

  const normalizeRegions = (rawRegions: any[]): string[] => {
    const regionObjects = rawRegions.filter((region) => region && typeof region === 'object' && !Array.isArray(region));
    const oldStyleRegions = regionObjects.flatMap((region) => Object.keys(region));
    return rawRegions
      .filter((region) => typeof region === 'string')
      .concat(oldStyleRegions)
      .sort();
  };

  const deriveRegions = (loadedRegions: string[]) => {
    const accountFilter = (cluster: any) => (cluster ? cluster.account === component.credentials : true);
    const regionList = AppListExtractor.getRegions([application], accountFilter);
    return (showAllRegions ? loadedRegions : regionList.length ? regionList : loadedRegions).slice().sort();
  };

  const deriveClusters = () => {
    const regionField = singleRegion ? component.region : component.regions;
    const clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(component.credentials, regionField);
    return AppListExtractor.getClusters([application], clusterFilter);
  };

  const refreshLists = (loadedRegions = allRegions) => {
    const nextRegions = clusterIsTextInput ? loadedRegions : deriveRegions(loadedRegions);
    const nextClusters = deriveClusters();
    setRegions(nextRegions);
    setClusterList(nextClusters);
    return nextClusters;
  };

  React.useEffect(() => {
    AccountService.getUniqueAttributeForAllAccounts(component.cloudProviderType, 'regions').then(
      (rawRegions: any[]) => {
        const normalizedRegions = normalizeRegions(rawRegions || []);
        const nextClusters = deriveClusters();
        const hasCustomCluster = component[clusterField] && !nextClusters.includes(component[clusterField]);
        const clusterIsInList = nextClusters.includes(component[clusterField]);
        setAllRegions(normalizedRegions);
        setClusterIsTextInput(Boolean(hasCustomCluster));
        setRegions(clusterIsInList ? deriveRegions(normalizedRegions) : normalizedRegions);
        setClusterList(nextClusters);
      },
    );
  }, [application, component]);

  const accountChanged = (account: string) => {
    component.credentials = account;
    component[clusterField] = undefined;
    refreshLists();
    props.onAccountUpdate && props.onAccountUpdate(account);
  };

  const regionChanged = (value: string | string[]) => {
    if (singleRegion) {
      component.region = value;
    } else {
      component.regions = value;
    }
    const nextClusters = refreshLists();
    if (!clusterIsTextInput && !nextClusters.includes(component[clusterField])) {
      component[clusterField] = undefined;
    }
  };

  const clusterChanged = (clusterName: string) => {
    component[clusterField] = clusterName || undefined;
    const filterByCluster = AppListExtractor.monikerClusterNameFilter(clusterName);
    const clusterMoniker = AppListExtractor.getMonikers([application], filterByCluster)[0];
    component.moniker = clusterMoniker ? { ...clusterMoniker, sequence: null } : undefined;
  };

  const toggleClusterTextInput = () => {
    setClusterIsTextInput(true);
    setRegions(allRegions);
  };

  const toggleClusterSelectInput = () => {
    component[clusterField] = undefined;
    setClusterIsTextInput(false);
    setRegions(deriveRegions(allRegions));
    setClusterList(deriveClusters());
  };

  return (
    <>
      <StageConfigField label="Account">
        <AccountSelectInput
          accounts={accounts}
          name="credentials"
          onChange={(event: React.ChangeEvent<HTMLSelectElement>) => accountChanged(event.target.value)}
          provider={component.cloudProviderType}
          value={component.credentials || ''}
        />
      </StageConfigField>
      <StageConfigField label={singleRegion ? 'Region' : 'Regions'}>
        {!component.credentials && <p className="form-control-static">(Select an Account)</p>}
        {component.credentials && singleRegion && (
          <select
            className="form-control input-sm region-select"
            disabled={disableRegionSelect}
            onChange={(event) => regionChanged(event.target.value)}
            required={true}
            value={component.region || ''}
          >
            <option value="">Select a region...</option>
            {regions.map((region) => (
              <option key={region} value={region}>
                {region}
              </option>
            ))}
          </select>
        )}
        {component.credentials && !singleRegion && (
          <ChecklistInput
            inline={true}
            name="regions"
            onChange={(event: any) => regionChanged(event.target.value)}
            showSelectAll={true}
            stringOptions={regions}
            value={component.regions || []}
          />
        )}
      </StageConfigField>
      {showClusterSelect && (
        <StageConfigField label="Cluster" helpKey="pipeline.config.findAmi.cluster">
          {clusterIsTextInput ? (
            <div>
              <input
                className="form-control input-sm cluster-text-input"
                onChange={(event) => clusterChanged(event.target.value)}
                type="text"
                value={component[clusterField] || ''}
              />
              <button
                className="btn btn-link btn-sm cluster-select-toggle"
                onClick={toggleClusterSelectInput}
                type="button"
              >
                Select an existing cluster
              </button>
            </div>
          ) : (
            <div>
              <select
                className="form-control input-sm cluster-select"
                onChange={(event) => clusterChanged(event.target.value)}
                value={component[clusterField] || ''}
              >
                <option value="">Select a cluster...</option>
                {clusterList.map((cluster) => (
                  <option key={cluster} value={cluster}>
                    {cluster}
                  </option>
                ))}
              </select>
              <button
                className="btn btn-link btn-sm cluster-text-toggle"
                onClick={toggleClusterTextInput}
                type="button"
              >
                Enter a cluster name
              </button>
            </div>
          )}
        </StageConfigField>
      )}
    </>
  );
}
