import { flattenDeep } from 'lodash';
import React from 'react';

import type { Application } from '@spinnaker/core';
import {
  AccountTag,
  CollapsibleSection,
  InstanceReader,
  RecentHistoryService,
  Spinner,
  timestamp,
} from '@spinnaker/core';

import { ProxmoxInstanceActions } from './ProxmoxInstanceActions';

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

const detailRow = (label: string, value: React.ReactNode): JSX.Element | null =>
  value == null || value === '' ? null : (
    <>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </>
  );

const yesNo = (value: boolean | undefined): string | null => (value == null ? null : value ? 'Yes' : 'No');

const formatUptime = (seconds: number): string | null => {
  if (seconds == null || seconds <= 0) {
    return null;
  }
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const parts: string[] = [];
  if (days) {
    parts.push(`${days}d`);
  }
  if (hours) {
    parts.push(`${hours}h`);
  }
  parts.push(`${minutes}m`);
  return parts.join(' ');
};

const diskDeviceLabel = (device: string, config: string): string => {
  if (device.startsWith('efidisk')) {
    return `${device} (EFI)`;
  }
  if (device.startsWith('tpmstate')) {
    return `${device} (TPM)`;
  }
  if (device.startsWith('unused')) {
    return `${device} (detached)`;
  }
  if (config.includes('cloudinit')) {
    return `${device} (cloud-init)`;
  }
  if (config.includes('media=cdrom')) {
    return `${device} (CD/DVD)`;
  }
  return device;
};

const formatBytes = (bytes: number): string | null => {
  if (bytes == null) {
    return null;
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value = value / 1024;
    unit++;
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
};

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
        <a
          className="btn btn-link"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            history.back();
          }}
        >
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
          <div className="actions">
            <ProxmoxInstanceActions app={this.props.app} instance={stateInstance} />
          </div>
        </div>
        <div className="content">
          <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
            <dl className="dl-horizontal dl-narrow">
              <dt>Account</dt>
              <dd>
                <AccountTag account={stateInstance.account} />
              </dd>
              {detailRow('Node', stateInstance.region)}
              {detailRow('Server Group', stateInstance.serverGroup)}
              {detailRow('VM ID', stateInstance.vmId)}
              {detailRow(
                'Type',
                stateInstance.vmType === 'qemu' ? 'QEMU VM' : stateInstance.vmType === 'lxc' ? 'LXC Container' : null,
              )}
              {detailRow('Status', stateInstance.status)}
              {detailRow('QMP Status', stateInstance.qmpStatus)}
              {detailRow('Uptime', formatUptime(stateInstance.uptimeSeconds))}
              {detailRow('Launched', stateInstance.launchTime ? timestamp(stateInstance.launchTime) : null)}
              {detailRow('HA Managed', yesNo(stateInstance.haManaged))}
              {detailRow('Start on Boot', yesNo(stateInstance.onBoot))}
              {detailRow('Protected', yesNo(stateInstance.protection))}
            </dl>
          </CollapsibleSection>

          {(stateInstance.cpus != null ||
            stateInstance.memoryMb != null ||
            stateInstance.diskGb != null ||
            stateInstance.osType) && (
            <CollapsibleSection heading="VM Configuration" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                {detailRow(
                  'vCPUs',
                  stateInstance.cpus != null
                    ? `${stateInstance.cpus}${
                        stateInstance.cores != null
                          ? ` (${stateInstance.sockets ?? 1} socket × ${stateInstance.cores} cores)`
                          : ''
                      }`
                    : null,
                )}
                {detailRow('Memory', stateInstance.memoryMb != null ? `${stateInstance.memoryMb} MB` : null)}
                {detailRow('Swap', stateInstance.swapMb != null ? `${stateInstance.swapMb} MB` : null)}
                {detailRow('Disk', stateInstance.diskGb != null ? `${stateInstance.diskGb} GB` : null)}
                {detailRow('OS Type', stateInstance.osType)}
                {detailRow('Machine', stateInstance.machine)}
                {detailRow('BIOS', stateInstance.bios)}
                {detailRow('Boot Order', stateInstance.bootOrder)}
                {detailRow('SCSI Controller', stateInstance.scsiController)}
                {detailRow('QEMU Agent', yesNo(stateInstance.agentEnabled))}
              </dl>
            </CollapsibleSection>
          )}

          {(stateInstance.disks && Object.keys(stateInstance.disks).length > 0) || stateInstance.disk0 ? (
            <CollapsibleSection heading="Storage" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                {stateInstance.disks && Object.keys(stateInstance.disks).length > 0
                  ? Object.entries(stateInstance.disks).map(([device, config]) => (
                      <React.Fragment key={device}>
                        <dt>{diskDeviceLabel(device, `${config}`)}</dt>
                        <dd>{`${config}`}</dd>
                      </React.Fragment>
                    ))
                  : detailRow('Boot Disk', stateInstance.disk0)}
              </dl>
            </CollapsibleSection>
          ) : null}

          {(stateInstance.cpuUsage != null ||
            stateInstance.memoryUsedMb != null ||
            stateInstance.diskUsedGb != null ||
            stateInstance.networkInBytes != null) && (
            <CollapsibleSection heading="Utilization" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                {detailRow(
                  'CPU Usage',
                  stateInstance.cpuUsage != null ? `${(stateInstance.cpuUsage * 100).toFixed(1)}%` : null,
                )}
                {detailRow(
                  'Memory Used',
                  stateInstance.memoryUsedMb != null
                    ? `${stateInstance.memoryUsedMb} MB${
                        stateInstance.memoryMb != null ? ` of ${stateInstance.memoryMb} MB` : ''
                      }`
                    : null,
                )}
                {detailRow(
                  'Swap Used',
                  stateInstance.swapUsedMb != null
                    ? `${stateInstance.swapUsedMb} MB${
                        stateInstance.swapMb != null ? ` of ${stateInstance.swapMb} MB` : ''
                      }`
                    : null,
                )}
                {detailRow(
                  'Disk Used',
                  stateInstance.diskUsedGb != null
                    ? `${stateInstance.diskUsedGb} GB${
                        stateInstance.diskGb != null ? ` of ${stateInstance.diskGb} GB` : ''
                      }`
                    : null,
                )}
                {detailRow('Network In', formatBytes(stateInstance.networkInBytes))}
                {detailRow('Network Out', formatBytes(stateInstance.networkOutBytes))}
                {detailRow('Disk Read', formatBytes(stateInstance.diskReadBytes))}
                {detailRow('Disk Write', formatBytes(stateInstance.diskWriteBytes))}
              </dl>
            </CollapsibleSection>
          )}

          {stateInstance.net0 && (
            <CollapsibleSection heading="Network" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">{detailRow('net0', stateInstance.net0)}</dl>
            </CollapsibleSection>
          )}

          {stateInstance.tags && Object.keys(stateInstance.tags).length > 0 && (
            <CollapsibleSection heading="Tags" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                {Object.entries(stateInstance.tags).map(([key, value]) => (
                  <React.Fragment key={key}>
                    <dt>{key}</dt>
                    <dd>{`${value}`}</dd>
                  </React.Fragment>
                ))}
              </dl>
            </CollapsibleSection>
          )}

          {stateInstance.description && (
            <CollapsibleSection heading="Notes" defaultExpanded={false}>
              <p>{stateInstance.description}</p>
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
