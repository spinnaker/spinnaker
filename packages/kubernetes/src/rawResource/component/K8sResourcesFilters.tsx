import React from 'react';

import { Application, ApplicationDataSource, FilterCheckbox, FilterSection } from '@spinnaker/core';

import { RawResourceUtils } from './RawResourceUtils';
import { FiltersPubSub } from '../controller/FiltersPubSub';
import { KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY } from '../rawResource.dataSource';

export interface IK8sResourcesFiltersProps {
  app: Application;
}

export interface IK8sResourcesFiltersState {
  accounts: Record<string, boolean>;
  kinds: Record<string, boolean>;
  namespaces: Record<string, boolean>;
  displayNamespaces: Record<string, boolean>;
}

export class K8sResourcesFilters extends React.Component<IK8sResourcesFiltersProps, IK8sResourcesFiltersState> {
  private dataSource: ApplicationDataSource<IApiKubernetesResource[]>;
  private filterPubSub: FiltersPubSub = FiltersPubSub.getInstance(this.props.app.name);

  constructor(props: IK8sResourcesFiltersProps) {
    super(props);
    this.dataSource = this.props.app.getDataSource(KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY);

    this.state = {
      accounts: {},
      kinds: {},
      namespaces: {},
      displayNamespaces: {},
    };
  }

  public async componentDidMount() {
    await this.dataSource.ready();
    const ns = Object.assign({}, ...this.dataSource.data.map((resource) => ({ [resource.namespace]: false })));
    const displayNs = { ...ns };
    if ('' in displayNs) {
      delete displayNs[''];
      displayNs[RawResourceUtils.GLOBAL_LABEL] = false;
    }
    this.setState({
      accounts: Object.assign({}, ...this.dataSource.data.map((resource) => ({ [resource.account]: false }))),
      kinds: Object.assign({}, ...this.dataSource.data.map((resource) => ({ [resource.kind]: false }))),
      namespaces: ns,
      displayNamespaces: displayNs,
    });
  }

  public render() {
    return (
      <div className="content">
        <FilterSection heading={'Kind'} expanded={true}>
          {...Object.keys(this.state.kinds)
            .sort((a, b) => a.localeCompare(b))
            .map((key) => (
              <FilterCheckbox
                heading={key}
                key={key}
                sortFilterType={this.state.kinds}
                onChange={this.onCheckbox.bind(this)}
              ></FilterCheckbox>
            ))}
        </FilterSection>
        <FilterSection heading={'Account'} expanded={true}>
          {...Object.keys(this.state.accounts)
            .sort((a, b) => a.localeCompare(b))
            .map((key) => (
              <FilterCheckbox
                heading={key}
                key={key}
                sortFilterType={this.state.accounts}
                onChange={this.onCheckbox.bind(this)}
              ></FilterCheckbox>
            ))}
        </FilterSection>
        <FilterSection heading={'Namespace'} expanded={true}>
          {...Object.keys(this.state.displayNamespaces)
            .sort((a, b) => a.localeCompare(b))
            .map((key) => (
              <FilterCheckbox
                heading={key}
                key={key}
                sortFilterType={this.state.displayNamespaces}
                onChange={this.onNsCheckbox.bind(this)}
              ></FilterCheckbox>
            ))}
        </FilterSection>
      </div>
    );
  }

  private onNsCheckbox() {
    const { namespaces, displayNamespaces } = { ...this.state };
    for (const p in displayNamespaces) {
      if (p == RawResourceUtils.GLOBAL_LABEL) {
        namespaces[''] = displayNamespaces[p];
      }
      namespaces[p] = displayNamespaces[p];
    }
    this.setState({ namespaces });
    this.filterPubSub.publish(this.state);
  }

  private onCheckbox() {
    this.setState(this.state);
    this.filterPubSub.publish(this.state);
  }
}
