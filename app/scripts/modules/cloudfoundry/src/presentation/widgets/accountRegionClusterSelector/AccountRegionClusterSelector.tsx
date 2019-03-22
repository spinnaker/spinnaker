import * as React from 'react';

import Select, { Option } from 'react-select';

import {
  Application,
  AppListExtractor,
  IAccount,
  IServerGroup,
  IServerGroupFilter,
  StageConfigField,
} from '@spinnaker/core';

export interface IAccountRegionClusterSelectorProps {
  accounts: IAccount[];
  application: Application;
  cloudProvider: string;
  clusterField?: string;
  component: any;
  componentName?: string;
  isSingleRegion?: boolean;
  onComponentUpdate?: (component: any) => void;
}

export interface IAccountRegionClusterSelectorState {
  [k: string]: any;

  availableRegions: string[];
  clusterField: string;
  clusters: string[];
  componentName: string;
}

export class AccountRegionClusterSelector extends React.Component<
  IAccountRegionClusterSelectorProps,
  IAccountRegionClusterSelectorState
> {
  constructor(props: IAccountRegionClusterSelectorProps) {
    super(props);
    const clusterField = props.clusterField || 'cluster';
    this.state = {
      availableRegions: [],
      cloudProvider: props.cloudProvider,
      clusterField: clusterField,
      clusters: [],
      componentName: props.componentName || '',
      [clusterField]: props.component[clusterField],
    };
  }

  public componentDidMount(): void {
    this.setRegionList(this.props.component.credentials);
    this.setClusterList(
      this.props.component.credentials,
      this.props.isSingleRegion ? [this.props.component.region] : this.props.component.regions,
    );
  }

  private setRegionList = (credentials: string): void => {
    const { application } = this.props;
    const accountFilter: IServerGroupFilter = (serverGroup: IServerGroup) =>
      serverGroup ? serverGroup.account === credentials : true;
    application.ready().then(() => {
      const availableRegions = AppListExtractor.getRegions([application], accountFilter);
      availableRegions.sort();
      this.setState({ availableRegions });
    });
  };

  private setClusterList = (credentials: string, regions: string[]): void => {
    const { application } = this.props;
    application.ready().then(() => {
      const clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(credentials, regions);
      const clusters = AppListExtractor.getClusters([application], clusterFilter);
      this.setState({ clusters });
    });
  };

  public onAccountUpdate = (option: Option<string>): void => {
    const credentials = option.value;
    this.setRegionList(credentials);
    this.setClusterList(credentials, []);
    this.props.onComponentUpdate &&
      this.props.onComponentUpdate({
        ...this.props.component,
        credentials,
        region: '',
        regions: [],
        [this.state.clusterField]: undefined,
      });
  };

  public onRegionsUpdate = (option: Option<string>): void => {
    const regions = option.map((o: Option) => o.value);
    this.setClusterList(this.props.component.credentials, regions);
    this.props.onComponentUpdate &&
      this.props.onComponentUpdate({
        ...this.props.component,
        regions,
        [this.state.clusterField]: undefined,
      });
  };

  public onRegionUpdate = (option: Option<string>): void => {
    const region = option.value;
    this.setClusterList(this.props.component.credentials, [region]);
    this.props.onComponentUpdate &&
      this.props.onComponentUpdate({
        ...this.props.component,
        region,
        [this.state.clusterField]: undefined,
      });
  };

  public onClusterUpdate = (option: Option<string>): void => {
    this.props.onComponentUpdate &&
      this.props.onComponentUpdate({
        ...this.props.component,
        [this.state.clusterField]: option.value,
      });
  };

  public render() {
    const { accounts, isSingleRegion, component } = this.props;
    const { availableRegions, clusters, clusterField, componentName } = this.state;
    return (
      <>
        <StageConfigField label="Account">
          <Select
            name={componentName ? `${componentName}.credentials` : 'credentials'}
            options={
              accounts &&
              accounts.map((acc: IAccount) => ({
                label: acc.name,
                value: acc.name,
              }))
            }
            clearable={false}
            value={component.credentials}
            onChange={this.onAccountUpdate}
          />
        </StageConfigField>

        {!isSingleRegion && (
          <StageConfigField label="Region">
            <Select
              name={componentName ? `${componentName}.regions` : 'regions'}
              options={
                availableRegions &&
                availableRegions.map((r: string) => ({
                  label: r,
                  value: r,
                }))
              }
              multi={true}
              clearable={false}
              value={component.regions}
              onChange={this.onRegionsUpdate}
            />
          </StageConfigField>
        )}
        {isSingleRegion && (
          <StageConfigField label="Region">
            <Select
              name={componentName ? `${componentName}.region` : 'region'}
              options={
                availableRegions &&
                availableRegions.map((r: string) => ({
                  label: r,
                  value: r,
                }))
              }
              multi={false}
              clearable={false}
              value={component.region}
              onChange={this.onRegionUpdate}
            />
          </StageConfigField>
        )}
        <StageConfigField label="Cluster" helpKey={'pipeline.config.findAmi.cluster'}>
          <Select
            name={componentName ? `${componentName}.${clusterField}` : `${clusterField}`}
            options={
              clusters &&
              clusters.map((c: string) => ({
                label: c,
                value: c,
              }))
            }
            clearable={false}
            value={component[clusterField]}
            onChange={this.onClusterUpdate}
          />
        </StageConfigField>
      </>
    );
  }
}
