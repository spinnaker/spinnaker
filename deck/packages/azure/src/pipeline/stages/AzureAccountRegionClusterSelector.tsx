import React from 'react';

import { AccountService, StageConfigField } from '@spinnaker/core';

interface IAzureAccountRegionClusterSelectorProps {
  application: any;
  stage: any;
  updateStageField: (changes: any) => void;
  onAccountUpdate?: (account: string) => void;
}

interface IAzureAccountRegionClusterSelectorState {
  accounts: any[];
  freeFormClusterField: boolean;
  regions: string[];
}

function uniqueSorted(values: string[]): string[] {
  return Array.from(new Set(values.filter(Boolean))).sort();
}

function regionName(region: any): string {
  if (typeof region === 'string') {
    return region;
  }
  if (region?.name) {
    return region.name;
  }

  const keys = Object.keys(region || {});
  return keys.length ? keys[0] : '';
}

export class AzureAccountRegionClusterSelector extends React.Component<
  IAzureAccountRegionClusterSelectorProps,
  IAzureAccountRegionClusterSelectorState
> {
  private mounted = false;

  public state: IAzureAccountRegionClusterSelectorState = {
    accounts: [],
    freeFormClusterField: false,
    regions: [],
  };

  public componentDidMount(): void {
    this.mounted = true;

    Promise.all([
      AccountService.listAccounts('azure'),
      AccountService.getUniqueAttributeForAllAccounts('azure', 'regions'),
    ])
      .then(([accounts, regions]) => {
        if (this.mounted) {
          this.setState({
            accounts,
            freeFormClusterField: this.selectedClusterIsCustom(),
            regions: uniqueSorted((regions || []).map(regionName)),
          });
        }
      })
      .catch(() => {});
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private serverGroups(): any[] {
    try {
      return this.props.application.getDataSource('serverGroups').data || [];
    } catch (_error) {
      return [];
    }
  }

  private accountOptions(): string[] {
    const accountNames = this.state.accounts.map((account) => account.name || account).filter(Boolean);
    return uniqueSorted([...accountNames, this.props.stage.credentials]);
  }

  private regionOptions(): string[] {
    const { credentials, regions = [] } = this.props.stage;
    const serverGroupRegions = this.serverGroups()
      .filter((serverGroup) => !credentials || serverGroup.account === credentials)
      .map((serverGroup) => serverGroup.region);

    const availableRegions = this.state.freeFormClusterField
      ? this.state.regions
      : serverGroupRegions.length
      ? serverGroupRegions
      : this.state.regions;

    return uniqueSorted([...availableRegions, ...regions]);
  }

  private clusterOptions(regionsOverride = this.props.stage.regions || []): string[] {
    const { credentials } = this.props.stage;
    const clusters = this.serverGroups()
      .filter((serverGroup) => !credentials || serverGroup.account === credentials)
      .filter((serverGroup) => !regionsOverride.length || regionsOverride.includes(serverGroup.region))
      .map((serverGroup) => serverGroup.cluster || serverGroup.moniker?.cluster);

    return uniqueSorted(clusters);
  }

  private selectedClusterIsCustom(): boolean {
    const cluster = this.props.stage.cluster;
    return !!cluster && !this.clusterOptions().includes(cluster);
  }

  private monikerForCluster(cluster: string): any {
    const serverGroup = this.serverGroups().find(
      (candidate) => candidate.cluster === cluster || candidate.moniker?.cluster === cluster,
    );
    return serverGroup?.moniker ? { ...serverGroup.moniker, sequence: null } : undefined;
  }

  private updateStageField(changes: any): void {
    Object.assign(this.props.stage, changes);
    this.props.updateStageField(changes);
  }

  private updateAccount(credentials: string): void {
    this.setState({ freeFormClusterField: false });
    this.updateStageField({ credentials, regions: [], cluster: undefined, moniker: undefined });
    this.props.onAccountUpdate?.(credentials);
  }

  private updateRegion(region: string, selected: boolean): void {
    const regions = new Set(this.props.stage.regions || []);
    selected ? regions.add(region) : regions.delete(region);
    const nextRegions = Array.from(regions);
    const selectedCluster = this.props.stage.cluster;
    const clusterStillAvailable =
      !selectedCluster || (!!nextRegions.length && this.clusterOptions(nextRegions).includes(selectedCluster));

    if (this.state.freeFormClusterField || clusterStillAvailable) {
      this.updateStageField({ regions: nextRegions });
      return;
    }

    this.updateStageField({ regions: nextRegions, cluster: undefined, moniker: undefined });
  }

  private updateCluster(cluster: string): void {
    this.setState({ freeFormClusterField: false });
    this.updateStageField({
      cluster: cluster || undefined,
      moniker: cluster ? this.monikerForCluster(cluster) : undefined,
    });
  }

  private updateFreeFormCluster(cluster: string): void {
    this.updateStageField({ cluster: cluster || undefined, moniker: undefined });
  }

  private toggleFreeFormClusterField(event: React.MouseEvent<HTMLAnchorElement>): void {
    event.preventDefault();
    const freeFormClusterField = !this.state.freeFormClusterField;
    this.setState({ freeFormClusterField });

    if (!freeFormClusterField) {
      this.updateStageField({ cluster: undefined, moniker: undefined });
    }
  }

  public render() {
    const stage = this.props.stage;
    const selectedRegions = stage.regions || [];

    return (
      <>
        <StageConfigField label="Account">
          <select
            className="form-control input-sm"
            name="credentials"
            value={stage.credentials || ''}
            onChange={(event) => this.updateAccount(event.target.value)}
          >
            <option value="" />
            {this.accountOptions().map((account) => (
              <option key={account} value={account}>
                {account}
              </option>
            ))}
          </select>
        </StageConfigField>
        <StageConfigField label="Regions">
          {!stage.credentials && <p className="form-control-static">(Select an Account)</p>}
          {stage.credentials &&
            this.regionOptions().map((region) => (
              <label className="checkbox-inline" key={region}>
                <input
                  checked={selectedRegions.includes(region)}
                  name="regions"
                  type="checkbox"
                  value={region}
                  onChange={(event) => this.updateRegion(region, event.target.checked)}
                />
                {region}
              </label>
            ))}
        </StageConfigField>
        <StageConfigField label="Cluster" helpKey="pipeline.config.findAmi.cluster">
          {this.state.freeFormClusterField ? (
            <input
              className="form-control input-sm"
              name="cluster"
              type="text"
              value={stage.cluster || ''}
              onChange={(event) => this.updateFreeFormCluster(event.target.value)}
            />
          ) : (
            <select
              className="form-control input-sm"
              name="cluster"
              value={stage.cluster || ''}
              onChange={(event) => this.updateCluster(event.target.value)}
            >
              <option value="" />
              {this.clusterOptions().map((cluster) => (
                <option key={cluster} value={cluster}>
                  {cluster}
                </option>
              ))}
            </select>
          )}
          <div className="pull-right">
            <a href="" onClick={(event) => this.toggleFreeFormClusterField(event)}>
              {this.state.freeFormClusterField ? 'Toggle for list of existing clusters' : 'Toggle for text input'}
            </a>
          </div>
        </StageConfigField>
      </>
    );
  }
}
