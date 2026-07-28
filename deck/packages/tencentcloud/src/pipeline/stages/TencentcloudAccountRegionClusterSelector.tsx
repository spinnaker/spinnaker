import { first, isNil, uniq } from 'lodash';
import React from 'react';

import type { Application, IAccountDetails, IMoniker, IServerGroup, IServerGroupFilter } from '@spinnaker/core';
import { AppListExtractor, ReactSelectInput, StageConfigField } from '@spinnaker/core';

export interface ITencentcloudAccountRegionClusterSelectorProps {
  accounts: IAccountDetails[];
  application: Application;
  clusterField?: string;
  component: any;
  setFieldValue: (field: string, value: any) => void;
}

export function getRegionNamesWithAccountFallback(
  applicationRegions: string[],
  accounts: IAccountDetails[],
  credentials: string,
  selectedRegions: string[] = [],
  useAccountRegions = false,
): string[] {
  const accountRegions = accounts
    .filter((account) => !credentials || account.name === credentials)
    .flatMap((account) => account.regions || [])
    .map((region) => region.name)
    .filter(Boolean);

  const selectedRegionMissingFromApplication = selectedRegions.some(
    (region) => !applicationRegions.includes(region) && accountRegions.includes(region),
  );

  if (applicationRegions.length && !selectedRegionMissingFromApplication && !useAccountRegions) {
    return uniq(applicationRegions).sort();
  }

  return uniq(accountRegions).sort();
}

export function getClusterSelectorState(
  clusterNames: string[],
  selectedCluster: string,
  currentCustomMode: boolean,
): { clusters: string[]; isCustomClusterInput: boolean } {
  const clusters = [...clusterNames];
  if (selectedCluster && !clusters.includes(selectedCluster)) {
    clusters.push(selectedCluster);
    return { clusters, isCustomClusterInput: true };
  }

  return { clusters, isCustomClusterInput: currentCustomMode };
}

export function TencentcloudAccountRegionClusterSelector({
  accounts,
  application,
  clusterField = 'cluster',
  component,
  setFieldValue,
}: ITencentcloudAccountRegionClusterSelectorProps) {
  const [availableRegions, setAvailableRegions] = React.useState<string[]>([]);
  const [clusters, setClusters] = React.useState<string[]>([]);
  const [isCustomClusterInput, setIsCustomClusterInput] = React.useState(false);
  const selectedClusterRef = React.useRef(component[clusterField]);
  selectedClusterRef.current = component[clusterField];

  React.useEffect(() => {
    let mounted = true;
    const accountFilter: IServerGroupFilter = (serverGroup: IServerGroup) =>
      serverGroup ? serverGroup.account === component.credentials : true;

    application.ready().then(() => {
      if (!mounted) {
        return;
      }
      const applicationRegions = AppListExtractor.getRegions([application], accountFilter);
      setAvailableRegions(
        getRegionNamesWithAccountFallback(
          applicationRegions,
          accounts,
          component.credentials,
          component.regions || [],
          isCustomClusterInput,
        ),
      );
    });

    return () => {
      mounted = false;
    };
  }, [accounts, application, component.credentials, component.regions, isCustomClusterInput]);

  React.useEffect(() => {
    let mounted = true;

    application.ready().then(() => {
      if (!mounted) {
        return;
      }

      const clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(
        component.credentials,
        component.regions || [],
      );
      const clusterNames = AppListExtractor.getClusters([application], clusterFilter);
      const { clusters: nextClusters, isCustomClusterInput: nextIsCustomClusterInput } = getClusterSelectorState(
        clusterNames,
        selectedClusterRef.current,
        isCustomClusterInput,
      );
      setIsCustomClusterInput(nextIsCustomClusterInput);
      setClusters(nextClusters);
    });

    return () => {
      mounted = false;
    };
  }, [application, clusterField, component.credentials, component.regions, isCustomClusterInput]);

  const onAccountUpdate = (event: React.ChangeEvent<HTMLInputElement>) => {
    setFieldValue('credentials', event.target.value);
    setFieldValue('regions', []);
    setFieldValue(clusterField, undefined);
  };

  const onRegionsUpdate = (event: React.ChangeEvent<HTMLInputElement>) => {
    setFieldValue('regions', event.target.value);
    if (!isCustomClusterInput) {
      setFieldValue(clusterField, undefined);
    }
  };

  const onClusterUpdate = (clusterName: string) => {
    const filterByCluster = AppListExtractor.monikerClusterNameFilter(clusterName);
    const clusterMoniker = first(uniq(AppListExtractor.getMonikers([application], filterByCluster))) as IMoniker;
    let moniker: IMoniker;

    if (isNil(clusterMoniker)) {
      moniker = undefined;
    } else {
      moniker = { ...clusterMoniker, sequence: null };
    }

    setFieldValue(clusterField, clusterName);
    setFieldValue('moniker', moniker);
  };

  const onClusterSelectUpdate = (event: React.ChangeEvent<HTMLSelectElement>) => {
    onClusterUpdate(event.target.value);
  };

  const onCustomClusterUpdate = (event: React.ChangeEvent<HTMLInputElement>) => {
    onClusterUpdate(event.target.value);
  };

  const toggleCustomClusterInput = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    const nextIsCustomClusterInput = !isCustomClusterInput;
    setIsCustomClusterInput(nextIsCustomClusterInput);
    if (!nextIsCustomClusterInput) {
      setFieldValue(clusterField, undefined);
    }
  };

  return (
    <>
      <StageConfigField label="Account">
        <ReactSelectInput
          clearable={false}
          name="credentials"
          onChange={onAccountUpdate}
          options={accounts.map((account) => ({ label: account.name, value: account.name }))}
          value={component.credentials}
        />
      </StageConfigField>
      <StageConfigField label="Region">
        <ReactSelectInput
          clearable={false}
          multi={true}
          name="regions"
          onChange={onRegionsUpdate}
          options={availableRegions.map((region) => ({ label: region, value: region }))}
          value={component.regions}
        />
      </StageConfigField>
      <StageConfigField helpKey="pipeline.config.findAmi.cluster" label="Cluster">
        {isCustomClusterInput ? (
          <input
            className="form-control input-sm"
            name={clusterField}
            onBlur={onCustomClusterUpdate}
            onChange={(event) => setFieldValue(clusterField, event.target.value)}
            type="text"
            value={component[clusterField] || ''}
          />
        ) : (
          <select
            className="form-control input-sm"
            name={clusterField}
            onChange={onClusterSelectUpdate}
            value={component[clusterField] || ''}
          >
            <option value="">-- select cluster --</option>
            {clusters.map((cluster) => (
              <option key={cluster} value={cluster}>
                {cluster}
              </option>
            ))}
          </select>
        )}
        <div className="pull-right">
          <a className="clickable" href="#" onClick={toggleCustomClusterInput}>
            {isCustomClusterInput ? 'Toggle for list of existing clusters' : 'Toggle for text input'}
          </a>
        </div>
      </StageConfigField>
    </>
  );
}
