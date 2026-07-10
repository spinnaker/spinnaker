import { flattenDeep } from 'lodash';
import React from 'react';

import type { Application } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, InstanceReader, RecentHistoryService, Spinner, timestamp } from '@spinnaker/core';

interface InstanceFromStateParams {
  instanceId: string;
}

interface InstanceManager {
  account: string;
  region: string;
  category: string;
  name: string;
  instances: any[];
}

interface IProxmoxInstanceDetailsProps {
  app: Application;
  instance: InstanceFromStateParams;
  loading: boolean;
}

interface IProxmoxInstanceDetailsState {
  instance?: any;
  instanceIdNotFound: string;
  loading: boolean;
}

export class ProxmoxInstanceDetails extends React.Component<
  IProxmoxInstanceDetailsProps,
  IProxmoxInstanceDetailsState
> {
  constructor(props: IProxmoxInstanceDetailsProps) {
    super(props);
    this.state = {
      loading: true,
      instanceIdNotFound: props.instance.instanceId,
    };
  }

  public componentDidMount(): void {
    this.props.app.ready().then(() => this.retrieveInstance(this.props.instance));
  }

  private retrieveInstance(instanceFromParams: InstanceFromStateParams): void {
    const dataSources: InstanceManager[] = flattenDeep([this.props.app.getDataSource('serverGroups').data]);

    const instanceManager = dataSources.find((source) =>
      source.instances.some((i) => i.name === instanceFromParams.instanceId || i.id === instanceFromParams.instanceId),
    );

    if (instanceManager) {
      RecentHistoryService.addExtraDataToLatest('instances', {
        region: instanceManager.region,
        account: instanceManager.account,
        serverGroup: instanceManager.name,
      });
      InstanceReader.getInstanceDetails(instanceManager.account, instanceManager.region, instanceFromParams.instanceId)
        .then((instanceDetails: any) => {
          instanceDetails.account = instanceManager.account;
          instanceDetails.region = instanceManager.region;
          instanceDetails.serverGroup = instanceManager.name;
          return instanceDetails;
        })
        .then((instance) => this.setState({ instance, loading: false }))
        .catch(() => this.setState({ loading: false }));
    } else {
      this.setState({ loading: false });
    }
  }

  public render(): JSX.Element {
    const { instance: stateInstance, instanceIdNotFound, loading } = this.state;

    const closeButton = (
      <div className="close-button">
        <a className="btn btn-link" href="#" onClick={(e) => { e.preventDefault(); history.back(); }}>
          <span className="glyphicon glyphicon-remove" />
        </a>
      </div>
    );

    if (loading) {
      return (
        <div className="details-panel">
          <div className="header">
            {closeButton}
            <div className="horizontal center middle">
              <Spinner size="small" />
            </div>
          </div>
        </div>
      );
    }

    if (!stateInstance) {
      return (
        <div className="details-panel">
          <div className="header">
            {closeButton}
            <div className="header-text horizontal middle">
              <h3 className="horizontal middle space-between flex-1">{instanceIdNotFound}</h3>
            </div>
          </div>
          <div className="content">
            <div className="content-section">
              <div className="content-body text-center">
                <h3>Instance not found.</h3>
              </div>
            </div>
          </div>
        </div>
      );
    }

    return (
      <div className="details-panel">
        <div className="header">
          {closeButton}
          <div className="header-text horizontal middle">
            <span className={`glyphicon glyphicon-hdd ${stateInstance.healthState ?? ''}`} />
            <h3 className="horizontal middle space-between flex-1">{stateInstance.name}</h3>
          </div>
        </div>
        <div className="content">
          <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
            <dl className="dl-horizontal dl-narrow">
              <dt>Account</dt>
              <dd>
                <AccountTag account={stateInstance.account} />
              </dd>
              <dt>Node</dt>
              <dd>{stateInstance.region}</dd>
              {stateInstance.serverGroup && (
                <>
                  <dt>Server Group</dt>
                  <dd>{stateInstance.serverGroup}</dd>
                </>
              )}
              {stateInstance.vmId != null && (
                <>
                  <dt>VM ID</dt>
                  <dd>{stateInstance.vmId}</dd>
                </>
              )}
              {stateInstance.status && (
                <>
                  <dt>Status</dt>
                  <dd>{stateInstance.status}</dd>
                </>
              )}
              {stateInstance.launchTime && (
                <>
                  <dt>Launched</dt>
                  <dd>{timestamp(stateInstance.launchTime)}</dd>
                </>
              )}
            </dl>
          </CollapsibleSection>

          {(stateInstance.cpus != null || stateInstance.memoryMb != null || stateInstance.diskGb != null || stateInstance.osType) && (
            <CollapsibleSection heading="VM Configuration" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                {stateInstance.cpus != null && (
                  <>
                    <dt>vCPUs</dt>
                    <dd>{stateInstance.cpus}</dd>
                  </>
                )}
                {stateInstance.memoryMb != null && (
                  <>
                    <dt>Memory</dt>
                    <dd>{stateInstance.memoryMb} MB</dd>
                  </>
                )}
                {stateInstance.diskGb != null && (
                  <>
                    <dt>Disk</dt>
                    <dd>{stateInstance.diskGb} GB</dd>
                  </>
                )}
                {stateInstance.osType && (
                  <>
                    <dt>OS Type</dt>
                    <dd>{stateInstance.osType}</dd>
                  </>
                )}
              </dl>
            </CollapsibleSection>
          )}

          {stateInstance.health?.length > 0 && (
            <CollapsibleSection heading="Health" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                {stateInstance.health.map((h: any, i: number) => (
                  <React.Fragment key={i}>
                    <dt>{h.type}</dt>
                    <dd>
                      <span className={`health-status-${h.state?.toLowerCase()}`}>{h.state}</span>
                    </dd>
                  </React.Fragment>
                ))}
              </dl>
            </CollapsibleSection>
          )}
        </div>
      </div>
    );
  }
}
