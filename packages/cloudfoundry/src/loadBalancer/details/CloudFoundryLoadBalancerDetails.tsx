import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';
import React from 'react';

import { Application, LoadBalancerWriter, Spinner } from '@spinnaker/core';

import { CloudFoundryLoadBalancerActions } from './CloudFoundryLoadBalancerActions';
import { ICloudFoundryLoadBalancer } from '../../domain';
import { CloudFoundryLoadBalancerDetailsSection } from './sections';
import { CloudFoundryLoadBalancerLinksSection } from './sections/CloudFoundryLoadBalancerLinksSection';
import { CloudFoundryLoadBalancerStatusSection } from './sections/CloudFoundryLoadBalancerStatusSection';

interface ILoadBalancer {
  name: string;
  accountId: string;
  region: string;
}

interface ICloudFoundryLoadBalancerDetailsState {
  loadBalancer: ICloudFoundryLoadBalancer;
  loadBalancerNotFound?: string;
  loading: boolean;
  refreshListenerUnsubscribe: () => void;
}

export interface ICloudFoundryLoadBalancerDetailsProps {
  app: Application;
  loadBalancer: ILoadBalancer;
  loadBalancerWriter: LoadBalancerWriter;
}

@UIRouterContext
export class CloudFoundryLoadBalancerDetails extends React.Component<
  ICloudFoundryLoadBalancerDetailsProps,
  ICloudFoundryLoadBalancerDetailsState
> {
  constructor(props: ICloudFoundryLoadBalancerDetailsProps) {
    super(props);
    this.state = {
      loading: true,
      loadBalancer: undefined,
      refreshListenerUnsubscribe: () => {},
    };

    props.app
      .getDataSource('loadBalancers')
      .ready()
      .then(() => this.extractLoadBalancer());
  }

  public componentWillUnmount(): void {
    this.state.refreshListenerUnsubscribe();
  }

  private extractLoadBalancer(): void {
    const { name } = this.props.loadBalancer;
    const loadBalancer: ICloudFoundryLoadBalancer = this.props.app
      .getDataSource('loadBalancers')
      .data.find((test: ICloudFoundryLoadBalancer) => {
        return test.name === name && test.account === this.props.loadBalancer.accountId;
      });

    this.setState({
      loading: false,
      loadBalancer,
    });

    this.state.refreshListenerUnsubscribe();

    if (loadBalancer) {
      this.setState({
        refreshListenerUnsubscribe: this.props.app
          .getDataSource('loadBalancers')
          .onRefresh(null, () => this.extractLoadBalancer()),
      });
    } else {
      this.setState({
        refreshListenerUnsubscribe: () => {},
      });
      // this.autoClose();
    }
  }

  public render(): JSX.Element {
    const { app } = this.props;
    const { loadBalancer, loadBalancerNotFound, loading } = this.state;

    const CloseButton = (
      <div className="close-button">
        <UISref to="^">
          <span className="glyphicon glyphicon-remove" />
        </UISref>
      </div>
    );
    const loadingHeader = () => (
      <div className="header">
        {CloseButton}
        <div className="horizontal center middle">
          <Spinner size="small" />
        </div>
      </div>
    );
    const notFoundHeader = () => (
      <div className="header">
        {CloseButton}
        <div className="header-text horizontal middle">
          <h3 className="horizontal middle space-between flex-1">{loadBalancerNotFound}</h3>
        </div>
      </div>
    );
    const loadBalancerHeader = () => (
      <div className="header">
        {CloseButton}
        <div className="header-text horizontal middle">
          <i className="fa icon-sitemap" />
          <h3 className="horizontal middle space-between flex-1">{loadBalancer.name}</h3>
        </div>
        <CloudFoundryLoadBalancerActions application={app} loadBalancer={loadBalancer} />
      </div>
    );
    const notFoundContent = () => (
      <div className="content">
        <div className="content-section">
          <div className="content-body text-center">
            <h3>Instance not found.</h3>
          </div>
        </div>
      </div>
    );
    const loadBalancerContent = () => (
      <div className="content">
        <CloudFoundryLoadBalancerDetailsSection loadBalancer={loadBalancer} />
        <CloudFoundryLoadBalancerStatusSection loadBalancer={loadBalancer} />
        <CloudFoundryLoadBalancerLinksSection loadBalancer={loadBalancer} />
      </div>
    );
    return (
      <div className="details-panel">
        {loading && loadingHeader()}
        {!loading && !!loadBalancer && loadBalancerHeader()}
        {!loading && !!loadBalancer && loadBalancerContent()}
        {!loading && !loadBalancer && notFoundHeader()}
        {!loading && !loadBalancer && notFoundContent()}
      </div>
    );
  }
}
