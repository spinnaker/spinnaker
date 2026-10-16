import React from 'react';

import type { Application } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, HealthCounts, Spinner } from '@spinnaker/core';

import type { IHaProxyLoadBalancer } from '../../HaProxyLoadBalancerTransformer';

interface ILoadBalancerFromStateParams {
  name: string;
  accountId: string;
  region: string;
}

export interface IHaProxyLoadBalancerDetailsProps {
  app: Application;
  loadBalancer: ILoadBalancerFromStateParams;
}

interface IHaProxyLoadBalancerDetailsState {
  loadBalancer?: IHaProxyLoadBalancer;
  loading: boolean;
  refreshUnsubscribe: () => void;
}

export class HaProxyLoadBalancerDetails extends React.Component<
  IHaProxyLoadBalancerDetailsProps,
  IHaProxyLoadBalancerDetailsState
> {
  constructor(props: IHaProxyLoadBalancerDetailsProps) {
    super(props);
    this.state = {
      loading: true,
      loadBalancer: undefined,
      refreshUnsubscribe: () => {},
    };
  }

  public componentDidMount(): void {
    const dataSource = this.props.app.getDataSource('loadBalancers');
    dataSource.ready().then(() => this.extractLoadBalancer());
    this.setState({ refreshUnsubscribe: dataSource.onRefresh(null, () => this.extractLoadBalancer()) });
  }

  public componentWillUnmount(): void {
    this.state.refreshUnsubscribe();
  }

  private extractLoadBalancer(): void {
    const { name, accountId, region } = this.props.loadBalancer;
    const loadBalancer = this.props.app
      .getDataSource('loadBalancers')
      .data.find((lb: IHaProxyLoadBalancer) => lb.name === name && lb.account === accountId && lb.region === region);
    this.setState({ loadBalancer, loading: false });
  }

  public render() {
    const { loadBalancer, loading } = this.state;

    if (loading) {
      return (
        <div className="details-panel">
          <div className="header">
            <div className="horizontal center middle spinner-container">
              <Spinner size="small" />
            </div>
          </div>
        </div>
      );
    }

    if (!loadBalancer) {
      return (
        <div className="details-panel">
          <div className="header">
            <h3 className="horizontal middle space-between flex-1">{this.props.loadBalancer.name} not found</h3>
          </div>
        </div>
      );
    }

    const binds = Object.entries(loadBalancer.binds || {});
    return (
      <div className="details-panel">
        <div className="header">
          <div className="header-text horizontal middle">
            <i className="fa icon-sitemap" />
            <h3 className="horizontal middle space-between flex-1">{loadBalancer.name}</h3>
          </div>
        </div>
        <div className="content">
          <CollapsibleSection heading="Load Balancer Details" defaultExpanded={true}>
            <dl className="dl-horizontal dl-narrow">
              <dt>Account</dt>
              <dd>
                <AccountTag account={loadBalancer.account} />
              </dd>
              <dt>Region</dt>
              <dd>{loadBalancer.region}</dd>
              <dt>Mode</dt>
              <dd>{loadBalancer.mode || 'http'}</dd>
              {loadBalancer.defaultBackend && (
                <>
                  <dt>Default Backend</dt>
                  <dd>{loadBalancer.defaultBackend}</dd>
                </>
              )}
              <dt>Health</dt>
              <dd>
                <HealthCounts container={loadBalancer.instanceCounts} />
              </dd>
            </dl>
          </CollapsibleSection>
          <CollapsibleSection heading="Binds" defaultExpanded={true}>
            {binds.length === 0 && <p>No binds configured.</p>}
            <dl className="dl-horizontal dl-narrow">
              {binds.map(([bindName, bind]) => (
                <React.Fragment key={bindName}>
                  <dt>{bindName}</dt>
                  <dd>
                    {bind.address || '*'}:{bind.port ?? '?'}
                    {bind.ssl ? ' (ssl)' : ''}
                  </dd>
                </React.Fragment>
              ))}
            </dl>
          </CollapsibleSection>
          <CollapsibleSection heading="Server Groups" defaultExpanded={true}>
            {(loadBalancer.serverGroups || []).map((serverGroup) => (
              <div key={serverGroup.name}>
                <b>{serverGroup.name}</b>
                {serverGroup.isDisabled ? ' (disabled)' : ''}
                <ul>
                  {(serverGroup.instances || []).map((instance) => (
                    <li key={instance.id}>
                      {instance.id}: {instance.healthState}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </CollapsibleSection>
        </div>
      </div>
    );
  }
}
