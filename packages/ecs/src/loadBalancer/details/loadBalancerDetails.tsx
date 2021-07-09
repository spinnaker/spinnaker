import { UISref, UISrefActive } from '@uirouter/react';
import React from 'react';

import { AccountTag, Application, CollapsibleSection, Spinner, timestamp } from '@spinnaker/core';

import { IEcsLoadBalancer } from '../../domain/IEcsLoadBalancer';
import { EcsListener } from '../listener';

interface IEcsLoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
  vpcId: string;
}

interface IEcsLoadBalancerDetailsState {
  loadBalancer: IEcsLoadBalancer;
  loadBalancerNotFound?: string;
  loading: boolean;
  refreshListenerUnsubscribe: () => void;
}

export interface IEcsLoadBalancerDetailsProps {
  app: Application;
  accountId: string;
  loadBalancer: IEcsLoadBalancerFromStateParams;
}

export class EcsLoadBalancerDetails extends React.Component<
  IEcsLoadBalancerDetailsProps,
  IEcsLoadBalancerDetailsState
> {
  constructor(props: IEcsLoadBalancerDetailsProps) {
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
    const { name, region } = this.props.loadBalancer;
    const loadBalancer: IEcsLoadBalancer = this.props.app
      .getDataSource('loadBalancers')
      .data.find((test: IEcsLoadBalancer) => {
        return test.name === name && test.account === this.props.loadBalancer.accountId && test.region === region;
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
    }
  }

  public render(): React.ReactElement<EcsLoadBalancerDetails> {
    const loadBalancerName = this.props.loadBalancer.name;
    const { loadBalancer, loading } = this.state;
    const { accountId } = this.props;

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

    const notFoundContent = () => (
      <div className="content">
        <div className="content-section">
          <div className="content-body text-center">
            <h3>Load balancer not found.</h3>
          </div>
        </div>
      </div>
    );

    const loadBalancerHeader = () => (
      <div className="header">
        {CloseButton}
        <div className="header-text horizontal middle">
          <i className="fa icon-sitemap" />
          <h3 className="horizontal middle space-between flex-1">{loadBalancerName}</h3>
        </div>
      </div>
    );

    const loadBalancerContent = () => (
      <div className="content">
        <CollapsibleSection heading="Load Balancer Details" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>Created</dt>
            <dd>{timestamp(loadBalancer.createdTime)}</dd>
            <dt>In</dt>
            <dd>
              <AccountTag account={accountId} />
              <br />
              {loadBalancer.region}
            </dd>
            <dt>VPC</dt>
            <dd>{loadBalancer.vpcId}</dd>
            <dt>Type</dt>
            <dd>{loadBalancer.loadBalancerType}</dd>
            <dt>IP Type</dt>
            <dd>{loadBalancer.ipAddressType}</dd>
          </dl>
          <dl className="horizontal-when-filters-collapsed">
            <dt>Availability Zones</dt>
            <dd>
              <ul>
                {loadBalancer.availabilityZones.map((az) => {
                  return <li key={az}>{az}</li>;
                })}
              </ul>
            </dd>
          </dl>
          <dl className="horizontal-when-filters-collapsed">
            <dt>Target Groups</dt>
            <dd>
              <ul>
                {loadBalancer.targetGroups.map((tg) => {
                  return (
                    <li key={tg.targetGroupName}>
                      <UISrefActive class="active">
                        <UISref
                          to="^.ecsTargetGroupDetails"
                          params={{
                            loadBalancerName: loadBalancer.name,
                            region: loadBalancer.region,
                            accountId: loadBalancer.account,
                            name: tg.targetGroupName,
                            vpcId: tg.vpcId,
                            provider: loadBalancer.cloudProvider,
                          }}
                        >
                          <a>{tg.targetGroupName}</a>
                        </UISref>
                      </UISrefActive>
                    </li>
                  );
                })}
              </ul>
            </dd>
          </dl>
          <dl className="horizontal-when-filters-collapsed">
            <dt>DNS Name</dt>
            <dd>
              <a target="_blank" href={'http://' + loadBalancer.dnsname}>
                {loadBalancer.dnsname}
              </a>
            </dd>
          </dl>
        </CollapsibleSection>
        <CollapsibleSection heading="Status" defaultExpanded={false}>
          <span>Select a target group to check the instance health status from the view of its server groups.</span>
        </CollapsibleSection>
        <CollapsibleSection heading="Listeners" defaultExpanded={false}>
          {loadBalancer.listeners ? (
            loadBalancer.listeners.map((listener, index) => {
              return (
                <div key={index}>
                  <span>
                    <EcsListener listener={listener}></EcsListener>
                  </span>
                </div>
              );
            })
          ) : (
            <li>No listeners provided.</li>
          )}
        </CollapsibleSection>
        <CollapsibleSection heading="Firewalls" defaultExpanded={false}>
          <ul>
            {loadBalancer.securityGroups ? (
              loadBalancer.securityGroups.map((sg) => {
                return <li key={sg}>{sg}</li>;
              })
            ) : (
              <li>No security groups provided.</li>
            )}
          </ul>
        </CollapsibleSection>
        <CollapsibleSection heading="Subnets" defaultExpanded={false}>
          <ul>
            {loadBalancer.subnets.map((subnet) => {
              return <li key={subnet}>{subnet}</li>;
            })}
          </ul>
        </CollapsibleSection>
      </div>
    );

    return (
      <div className="details-panel">
        {loading && loadingHeader()}
        {!loading && loadBalancerHeader()}
        {!loading && !loadBalancer && notFoundContent()}
        {!loading && loadBalancer && loadBalancerContent()}
      </div>
    );
  }
}
