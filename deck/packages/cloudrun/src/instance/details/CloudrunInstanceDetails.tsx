import { UISref } from '@uirouter/react';
import { flattenDeep } from 'lodash';
import React from 'react';

import type { Application, ILoadBalancer } from '@spinnaker/core';
import {
  AccountTag,
  CollapsibleSection,
  InstanceReader,
  RecentHistoryService,
  Spinner,
  timestamp,
} from '@spinnaker/core';

import type { ICloudrunInstance } from '../../common/domain';

interface ICloudrunInstanceDetailsProps {
  app: Application;
  instance: {
    instanceId: string;
  };
}

interface InstanceManager {
  account: string;
  region: string;
  category: string;
  name: string;
  instances: ICloudrunInstance[];
}

interface ICloudrunInstanceDetailsState {
  loading: boolean;
  instance?: ICloudrunInstance;
  instanceIdNotFound?: string;
}

export class CloudrunInstanceDetails extends React.Component<
  ICloudrunInstanceDetailsProps,
  ICloudrunInstanceDetailsState
> {
  public state: ICloudrunInstanceDetailsState = { loading: true };
  private isUnmounted = false;

  public componentDidMount(): void {
    this.props.app
      .ready()
      .then(() => this.retrieveInstance())
      .then((instance) => !this.isUnmounted && this.setState({ instance, loading: false }))
      .catch(
        () =>
          !this.isUnmounted && this.setState({ instanceIdNotFound: this.props.instance.instanceId, loading: false }),
      );
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
  }

  private retrieveInstance(): PromiseLike<ICloudrunInstance> {
    const { app, instance } = this.props;
    const dataSources: InstanceManager[] = flattenDeep([
      app.getDataSource('serverGroups').data,
      app.getDataSource('loadBalancers').data,
      app.getDataSource('loadBalancers').data.map((loadBalancer: ILoadBalancer) => loadBalancer.serverGroups),
    ]);
    const instanceManager = dataSources.find((dataSource) =>
      dataSource.instances.some((possibleMatch) => possibleMatch.id === instance.instanceId),
    );

    if (!instanceManager) {
      return Promise.reject();
    }

    const recentHistoryExtraData: { [key: string]: string } = {
      region: instanceManager.region,
      account: instanceManager.account,
    };
    if (instanceManager.category === 'serverGroup') {
      recentHistoryExtraData.serverGroup = instanceManager.name;
    }
    RecentHistoryService.addExtraDataToLatest('instances', recentHistoryExtraData);

    return InstanceReader.getInstanceDetails(instanceManager.account, instanceManager.region, instance.instanceId).then(
      (instanceDetails: ICloudrunInstance) => ({
        ...instanceDetails,
        account: instanceManager.account,
        region: instanceManager.region,
      }),
    );
  }

  public render() {
    const { loading, instance, instanceIdNotFound } = this.state;
    const upToolTip = "A Cloud Run instance is 'Up' if a load balancer is directing traffic to its server group.";
    const outOfServiceToolTip =
      "A Cloud Run instance is 'Out Of Service' if no load balancers are directing traffic to its server group.";

    return (
      <div className="details-panel">
        <div className="header">
          <div className="header-text horizontal middle">
            <h3 className="horizontal middle space-between flex-1">{instance ? instance.name : instanceIdNotFound}</h3>
          </div>
          {loading && <Spinner size="small" />}
        </div>
        {!loading && instance && (
          <div className="content">
            <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                <dt>Launched</dt>
                <dd>{instance.launchTime && timestamp(instance.launchTime)}</dd>
                <dt>In</dt>
                <dd>
                  <AccountTag account={instance.account} />
                </dd>
                {instance.serverGroup && <dt>Server Group</dt>}
                {instance.serverGroup && (
                  <dd>
                    <UISref
                      to="^.serverGroup"
                      params={{
                        region: instance.region,
                        accountId: instance.account,
                        serverGroup: instance.serverGroup,
                        provider: 'cloudrun',
                      }}
                    >
                      <a>{instance.serverGroup}</a>
                    </UISref>
                  </dd>
                )}
                <dt>Region</dt>
                <dd>{instance.region}</dd>
                {instance.vmZoneName && <dt>Zone</dt>}
                {instance.vmZoneName && <dd>{instance.vmZoneName}</dd>}
              </dl>
            </CollapsibleSection>
            <CollapsibleSection heading="Status" defaultExpanded={true}>
              <dl>
                <dt>Load Balancer</dt>
                <dd>
                  <span className="pull-left" title={instance.healthState === 'Up' ? upToolTip : outOfServiceToolTip}>
                    <span className={`glyphicon glyphicon-${instance.healthState}-triangle`} />{' '}
                    {instance.loadBalancers?.[0]}
                  </span>
                </dd>
              </dl>
            </CollapsibleSection>
          </div>
        )}
        {!loading && !instance && (
          <div className="content">
            <div className="content-section">
              <div className="content-body text-center">
                <h3>Instance not found.</h3>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
}
