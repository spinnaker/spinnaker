import { UISref } from '@uirouter/react';
import React from 'react';

import type { Application } from '@spinnaker/core';
import {
  AccountTag,
  CollapsibleSection,
  CopyToClipboard,
  HealthCounts,
  ManagedResourceDetailsIndicator,
  ReactInjector,
  Spinner,
} from '@spinnaker/core';

import type { IAmazonApplicationLoadBalancer, ITargetGroup } from '../domain';
import { VpcTag } from '../vpc/VpcTag';

interface IAmazonTargetGroupDetailsState {
  loadBalancer?: IAmazonApplicationLoadBalancer;
  loading: boolean;
  targetGroup?: ITargetGroup;
}

interface ITargetGroupStateParams {
  accountId: string;
  loadBalancerName: string;
  name: string;
  provider: string;
  region: string;
  vpcId?: string;
}

interface ITargetGroupDetailsProps {
  accountId: string;
  app: Application;
  name: string;
  provider: string;
  targetGroup: ITargetGroupStateParams;
}

function elbProtocol(loadBalancer: IAmazonApplicationLoadBalancer): string {
  return loadBalancer.listeners?.some((listener) => listener.protocol === 'HTTPS') ? 'https:' : 'http:';
}

export class TargetGroupDetails extends React.Component<ITargetGroupDetailsProps, IAmazonTargetGroupDetailsState> {
  public state: IAmazonTargetGroupDetailsState = { loading: true };

  private unsubscribeFromRefresh: () => void;
  private isUnmounted = false;

  public componentDidMount(): void {
    this.props.app
      .getDataSource('loadBalancers')
      .ready()
      .then(() => {
        if (this.isUnmounted) {
          return;
        }
        this.extractTargetGroup();
        this.unsubscribeFromRefresh = this.props.app
          .getDataSource('loadBalancers')
          .onRefresh(null, this.extractTargetGroup);
      })
      .catch(() => {
        if (!this.isUnmounted) {
          this.setState({ loading: false });
        }
      });
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    if (this.unsubscribeFromRefresh) {
      this.unsubscribeFromRefresh();
    }
  }

  private extractTargetGroup = (): void => {
    const { accountId, loadBalancerName, name, region } = this.props.targetGroup;
    const loadBalancer = this.props.app
      .getDataSource('loadBalancers')
      .data.find(
        (candidate: IAmazonApplicationLoadBalancer) =>
          candidate.name === loadBalancerName && candidate.region === region && candidate.account === accountId,
      );

    const targetGroup = loadBalancer?.targetGroups?.find((candidate: ITargetGroup) => candidate.name === name);

    if (!loadBalancer || !targetGroup) {
      this.autoClose();
      return;
    }

    if (!this.isUnmounted) {
      this.setState({ loadBalancer, targetGroup, loading: false });
    }
  };

  private autoClose = (): void => {
    if (this.isUnmounted) {
      return;
    }

    ReactInjector.$state.params.allowModalToStayOpen = true;
    ReactInjector.$state.go('^', null, { location: 'replace' });
  };

  private closeDetails = (): void => {
    ReactInjector.$state.go('^');
  };

  private renderHeader(): JSX.Element {
    const { name } = this.props;
    const { loading } = this.state;
    return (
      <div className="header">
        <div className="close-button">
          <a className="btn btn-link" onClick={this.closeDetails}>
            <span className="glyphicon glyphicon-remove" />
          </a>
        </div>
        {loading && (
          <div className="horizontal center middle">
            <Spinner size="small" />
          </div>
        )}
        {!loading && (
          <div className="header-text horizontal middle">
            <i className="fa fa-crosshairs icon" aria-hidden="true" />
            <h3 className="horizontal middle space-between flex-1">{name}</h3>
          </div>
        )}
      </div>
    );
  }

  private renderNotFound(): JSX.Element {
    return (
      <div className="content">
        <div className="content-section">
          <div className="content-body text-center">
            <h3>Target group not found.</h3>
          </div>
        </div>
      </div>
    );
  }

  private renderDetails(loadBalancer: IAmazonApplicationLoadBalancer, targetGroup: ITargetGroup) {
    const dnsName = (loadBalancer as any).dnsname || loadBalancer.elb?.dnsname;
    const attributes = (targetGroup.attributes || {}) as any;
    return (
      <div className="content">
        <CollapsibleSection heading="Target Group Details" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>In</dt>
            <dd>
              <AccountTag account={targetGroup.account} /> {targetGroup.region}
            </dd>
            <dt>VPC</dt>
            <dd>
              <VpcTag vpcId={targetGroup.vpcId} />
            </dd>
            <dt>Protocol</dt>
            <dd>{targetGroup.protocol}</dd>
            <dt>Port</dt>
            <dd>{targetGroup.port}</dd>
            <dt>Target Type</dt>
            <dd>{targetGroup.targetType}</dd>
          </dl>
          <dl className="horizontal-when-filters-collapsed">
            <dt>Load Balancer</dt>
            <dd>
              <ul className="collapse-margin-on-filter-collapse">
                <li>
                  <UISref
                    to="^.loadBalancerDetails"
                    params={{
                      name: loadBalancer.name,
                      region: loadBalancer.region,
                      accountId: loadBalancer.account,
                      vpcId: loadBalancer.vpcId,
                      provider: 'aws',
                    }}
                  >
                    <a>{loadBalancer.name}</a>
                  </UISref>
                </li>
              </ul>
            </dd>
          </dl>
          {dnsName && (
            <dl className="horizontal-when-filters-collapsed">
              <dt>Load Balancer DNS Name</dt>
              <dd>
                <a target="_blank" href={`${elbProtocol(loadBalancer)}//${dnsName}`}>
                  {dnsName}
                </a>{' '}
                <CopyToClipboard text={dnsName} toolTip="Copy DNS Name to clipboard" />
              </dd>
            </dl>
          )}
          {!!targetGroup.serverGroups?.length && (
            <dl className="horizontal-when-filters-collapsed">
              <dt>Server Groups</dt>
              <dd>
                <ul className="collapse-margin-on-filter-collapse">
                  {targetGroup.serverGroups.map((serverGroup: any) => (
                    <li key={serverGroup.name}>
                      <UISref
                        to="^.serverGroup"
                        params={{
                          region: serverGroup.region,
                          accountId: serverGroup.account,
                          serverGroup: serverGroup.name,
                          provider: serverGroup.cloudProvider,
                        }}
                      >
                        <a>{serverGroup.name}</a>
                      </UISref>
                    </li>
                  ))}
                </ul>
              </dd>
            </dl>
          )}
        </CollapsibleSection>
        <CollapsibleSection heading="Status" defaultExpanded={true}>
          <HealthCounts className="pull-left" container={targetGroup.instanceCounts} />
        </CollapsibleSection>
        <CollapsibleSection heading="Health Checks">
          <dl className="horizontal-when-filters-collapsed">
            <dt>Target</dt>
            <dd>
              {targetGroup.healthCheckProtocol}:{targetGroup.healthCheckPort}
              {targetGroup.healthCheckPath}
            </dd>
            <dt>Timeout</dt>
            <dd>{(targetGroup as any).healthCheckTimeoutSeconds || targetGroup.healthTimeout} seconds</dd>
            <dt>Interval</dt>
            <dd>{(targetGroup as any).healthCheckIntervalSeconds || targetGroup.healthInterval} seconds</dd>
            <dt>Healthy Threshold</dt>
            <dd>{(targetGroup as any).healthyThresholdCount || targetGroup.healthyThreshold}</dd>
            <dt>Unhealthy Threshold</dt>
            <dd>{(targetGroup as any).unhealthyThresholdCount || targetGroup.unhealthyThreshold}</dd>
            {(targetGroup as any).matcher && <dt>Matcher</dt>}
            {(targetGroup as any).matcher && <dd>HTTP Code(s): {(targetGroup as any).matcher.httpCode}</dd>}
          </dl>
        </CollapsibleSection>
        <CollapsibleSection heading="Attributes" defaultExpanded={true}>
          <dl className="horizontal-when-filters-collapsed">
            <dt>Deregistration Delay Timeout</dt>
            <dd>{attributes['deregistration_delay.timeout_seconds'] || attributes.deregistrationDelay} seconds</dd>
            <dt>Stickiness Enabled</dt>
            <dd>{String(attributes['stickiness.enabled'] ?? attributes.stickinessEnabled ?? false)}</dd>
            {(attributes['stickiness.enabled'] === 'true' || attributes.stickinessEnabled) && (
              <>
                <dt>Stickiness Load Balancer Cookie Duration</dt>
                <dd>{attributes['stickiness.lb_cookie.duration_seconds'] || attributes.stickinessDuration} seconds</dd>
                <dt>Stickiness Type</dt>
                <dd>{attributes['stickiness.type'] || attributes.stickinessType}</dd>
              </>
            )}
            {loadBalancer.loadBalancerType === 'network' && (
              <>
                <dt>Preserve Client IP</dt>
                <dd>{String(attributes['preserve_client_ip.enabled'] ?? attributes.preserveClientIp ?? false)}</dd>
              </>
            )}
          </dl>
        </CollapsibleSection>
      </div>
    );
  }

  public render(): JSX.Element {
    const { app } = this.props;
    const { loadBalancer, loading, targetGroup } = this.state;
    return (
      <div className="details-panel">
        {this.renderHeader()}
        {!loading && loadBalancer?.isManaged && (
          <ManagedResourceDetailsIndicator resourceSummary={loadBalancer.managedResourceSummary} application={app} />
        )}
        {!loading && !targetGroup && this.renderNotFound()}
        {!loading && targetGroup && loadBalancer && this.renderDetails(loadBalancer, targetGroup)}
      </div>
    );
  }
}
