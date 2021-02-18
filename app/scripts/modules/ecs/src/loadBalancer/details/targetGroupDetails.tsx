import { UISref, UISrefActive } from '@uirouter/react';
import React from 'react';

import { AccountTag, Application, CollapsibleSection, Spinner } from '@spinnaker/core';

import { IEcsLoadBalancer, IEcsTargetGroup } from '../../domain/IEcsLoadBalancer';

interface IEcsTargetGroupFromStateParams {
  loadBalancerName: string;
  targetGroupName: string;
  region: string;
  vpcId: string;
}

interface IEcsTargetGroupDetailsState {
  loadBalancer: IEcsLoadBalancer;
  targetGroup: IEcsTargetGroup;
  targetGroupNotFound?: string;
  loading: boolean;
  refreshListenerUnsubscribe: () => void;
}

export interface IEcsTargetGroupProps {
  app: Application;
  accountId: string;
  name: string;
  targetGroup: IEcsTargetGroupFromStateParams;
  provider: string;
}

export class EcsTargetGroupDetails extends React.Component<IEcsTargetGroupProps, IEcsTargetGroupDetailsState> {
  constructor(props: IEcsTargetGroupProps) {
    super(props);
    this.state = {
      loading: true,
      targetGroup: undefined,
      loadBalancer: undefined,
      refreshListenerUnsubscribe: () => {},
    };

    props.app
      .getDataSource('loadBalancers')
      .ready()
      .then(() => this.extractTargetGroup());
  }

  public componentWillUnmount(): void {
    this.state.refreshListenerUnsubscribe();
  }

  private extractTargetGroup(): void {
    const { loadBalancerName, region, targetGroupName } = this.props.targetGroup;
    const loadBalancer: IEcsLoadBalancer = this.props.app
      .getDataSource('loadBalancers')
      .data.find((test: IEcsLoadBalancer) => {
        return test.name === loadBalancerName && test.account === this.props.accountId && test.region === region;
      });

    this.setState({
      loadBalancer,
    });
    this.state.refreshListenerUnsubscribe();

    if (!loadBalancer) {
      this.setState({
        loading: false,
        targetGroup: null,
      });
    } else {
      const targetGroup: IEcsTargetGroup = loadBalancer.targetGroups.find(
        (tg) => tg.targetGroupName === targetGroupName,
      );
      if (!targetGroup) {
        this.setState({
          loading: false,
          targetGroup: null,
        });
      }

      if (targetGroup) {
        this.setState({
          loading: false,
          targetGroup,
        });

        if (targetGroup) {
          this.setState({
            refreshListenerUnsubscribe: this.props.app
              .getDataSource('loadBalancers')
              .onRefresh(null, () => this.extractTargetGroup()),
          });
        } else {
          this.setState({
            refreshListenerUnsubscribe: () => {},
          });
        }
      }
    }
  }

  public render(): React.ReactElement<EcsTargetGroupDetails> {
    const { name, accountId } = this.props;
    const region = this.props.targetGroup.region;
    const { targetGroup, loadBalancer, loading } = this.state;

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
            <h3>Target group not found.</h3>
          </div>
        </div>
      </div>
    );

    const targetGroupHeader = () => (
      <div className="header">
        <div className="header-text horizontal middle">
          <i className="fa fa-crosshairs icon" aria-hidden="true"></i>
          <h3 className="horizontal middle space-between flex-1">{name}</h3>
        </div>
      </div>
    );

    const targetGroupContent = () => (
      <div className="content">
        <CollapsibleSection heading="Target Group Details" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>In</dt>
            <dd>
              <AccountTag account={accountId}></AccountTag>
              <br />
              {targetGroup.region}
            </dd>
            <dt>VPC</dt>
            <dd>{targetGroup.vpcId}</dd>
            <dt>Protocol</dt>
            <dd>{targetGroup.protocol}</dd>
            <dt>Port</dt>
            <dd>{targetGroup.port}</dd>
            <dt>Target Type</dt>
            <dd>{targetGroup.targetType.toUpperCase()}</dd>
          </dl>
          <dl className="horizontal-when-filters-collapsed">
            <dt>Load Balancers</dt>
            <dd>
              {/* TODO: use UISref to display/highlight LBs in this app. See core/.../LoadBalancer.tsx */}
              <ul className="collapse-margin-on-filter-collapse">
                {targetGroup.loadBalancerNames ? (
                  targetGroup.loadBalancerNames.map((lb, i) => {
                    return <li key={i}>{lb}</li>;
                  })
                ) : (
                  <li>No load balncers provided.</li>
                )}
              </ul>
            </dd>
          </dl>
          <dl className="horizontal-when-filters-collapsed">
            <dt>Load Balancer DNS Name</dt>
            <dd>
              <a target="_blank" href={'http://' + loadBalancer.dnsname}>
                {loadBalancer.dnsname}
              </a>
            </dd>
          </dl>
          <dl className="horizontal-when-filters-collapsed">
            <dt>Server Groups</dt>
            <dd>
              <ul className="collapse-margin-on-filter-collapse">
                {targetGroup.serverGroups.map((sg, i) => {
                  return (
                    <li key={i}>
                      <UISrefActive class="active">
                        <UISref
                          to="^.serverGroup"
                          params={{
                            region: region,
                            accountId: accountId,
                            serverGroup: sg,
                            provider: 'ecs',
                          }}
                        >
                          <a>{sg}</a>
                        </UISref>
                      </UISrefActive>
                    </li>
                  );
                })}
              </ul>
            </dd>
          </dl>
        </CollapsibleSection>
        <CollapsibleSection heading="Health Checks" defaultExpanded={false}>
          <dl className="horizontal-when-filters-collapsed">
            <dt>Target</dt>
            <dd>
              {targetGroup.healthCheckProtocol}:{targetGroup.healthCheckPort}
              {targetGroup.healthCheckPath}
            </dd>
            <dt>Timeout</dt>
            <dd>{targetGroup.healthCheckTimeoutSeconds} seconds</dd>
            <dt>Interval</dt>
            <dd>{targetGroup.healthCheckIntervalSeconds} seconds</dd>
            <dt>Healthy Threshold</dt>
            <dd>{targetGroup.healthyThresholdCount}</dd>
            <dt>Unhealthy Threshold</dt>
            <dd>{targetGroup.unhealthyThresholdCount}</dd>
            <dt>Matcher</dt>
            <dd>HTTP Code(s): {targetGroup && targetGroup.matcher ? targetGroup.matcher.httpCode : 'None'}</dd>
          </dl>
        </CollapsibleSection>
        <CollapsibleSection heading="Attributes" defaultExpanded={false}>
          <dl>
            <dt>Deregistration Delay Timeout</dt>
            <dd>{targetGroup.attributes['deregistration_delay.timeout_seconds'] || 0} seconds</dd>
            <dt>Stickiness Enabled</dt>
            <dd>{targetGroup.attributes['stickiness.enabled'] || 'N/A'}</dd>
          </dl>
        </CollapsibleSection>
      </div>
    );

    return (
      <div className="details-panel">
        {loading && loadingHeader()}
        {!loading && targetGroupHeader()}
        {!loading && !targetGroup && notFoundContent()}
        {!loading && targetGroup && targetGroupContent()}
      </div>
    );
  }
}
