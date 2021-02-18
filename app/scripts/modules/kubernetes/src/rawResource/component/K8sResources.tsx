import React from 'react';

import { Application, ApplicationDataSource, FormField, ReactSelectInput } from '@spinnaker/core';

import { IK8sResourcesFiltersState } from './K8sResourcesFilters';
import { RawResourceUtils } from './RawResourceUtils';
import { FiltersPubSub } from '../controller/FiltersPubSub';
import { RawResource } from './group/RawResource';
import { RawResourceGroups } from './group/RawResourceGroups';
import { KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY } from '../rawResource.dataSource';

import './K8sResources.less';

export interface IK8sResourcesProps {
  app: Application;
}

interface IK8sResourcesState {
  groupBy: string;
  filters: IK8sResourcesFiltersState;
  rawResources: IApiKubernetesResource[];
}

export class K8sResources extends React.Component<IK8sResourcesProps, IK8sResourcesState> {
  private dataSource: ApplicationDataSource<IApiKubernetesResource[]>;
  private filterPubSub: FiltersPubSub = FiltersPubSub.getInstance(this.props.app.name);
  private sub = this.onFilterChange.bind(this);

  constructor(props: IK8sResourcesProps) {
    super(props);
    this.dataSource = this.props.app.getDataSource(KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY);
    this.state = {
      groupBy: 'none',
      filters: null,
      rawResources: [],
    };

    this.filterPubSub.subscribe(this.sub);
  }

  public componentWillUnmount() {
    this.filterPubSub.unsubscribe(this.sub);
  }

  public onFilterChange(message: IK8sResourcesFiltersState) {
    this.setState({ ...this.state, filters: message });
  }

  public async componentDidMount() {
    await this.dataSource.ready();

    this.setState({
      ...this.state,
      groupBy: this.state.groupBy,
      rawResources: await this.dataSource.data.sort((a, b) => a.name.localeCompare(b.name)),
    });
  }

  private groupByChanged = (e: React.ChangeEvent<any>): void => {
    this.setState({ groupBy: e.target.value.toLowerCase() });
  };

  public render() {
    const opts = ['None', 'Account', 'Kind', 'Namespace'];
    return (
      <div className="K8sResources">
        <div className="header row">
          <FormField
            onChange={this.groupByChanged}
            value={this.state.groupBy.charAt(0).toUpperCase() + this.state.groupBy.substr(1)}
            name="groupBy"
            label="Group By"
            input={(props) => (
              <ReactSelectInput {...props} inputClassName="groupby" stringOptions={opts} clearable={false} />
            )}
          />
        </div>
        <div className="content">
          {this.state.groupBy === 'none' ? (
            <>
              {...this.state.rawResources
                .filter((resource) => this.matchFilters(resource))
                .map((resource) => (
                  <RawResource key={RawResourceUtils.resourceKey(resource)} resource={resource}></RawResource>
                ))}
            </>
          ) : (
            <RawResourceGroups
              resources={this.state.rawResources.filter((resource) => this.matchFilters(resource))}
              groupBy={this.state.groupBy}
            ></RawResourceGroups>
          )}
        </div>
      </div>
    );
  }

  private matchFilters(resource: IApiKubernetesResource) {
    if (this.state.filters == null) {
      return true;
    }
    const accountMatch =
      Object.values(this.state.filters.accounts).every((x) => !x) || this.state.filters.accounts[resource.account];
    const kindMatch =
      Object.values(this.state.filters.kinds).every((x) => !x) || this.state.filters.kinds[resource.kind];
    const namespaceMatch =
      Object.values(this.state.filters.namespaces).every((x) => !x) ||
      this.state.filters.namespaces[resource.namespace];
    return accountMatch && kindMatch && namespaceMatch;
  }
}
