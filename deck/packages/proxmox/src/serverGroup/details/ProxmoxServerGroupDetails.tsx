import React from 'react';

import type { IInstance, IServerGroupActionsProps, IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, HealthCounts, timestamp } from '@spinnaker/core';

export function ProxmoxServerGroupActions(_props: IServerGroupActionsProps): JSX.Element {
  return null;
}

export function ProxmoxServerGroupInformationSection({ serverGroup }: IServerGroupDetailsSectionProps): JSX.Element {
  const sg = serverGroup as any;
  const lc = sg.launchConfig ?? {};
  const capacity = (serverGroup.capacity ?? {}) as any;

  return (
    <>
      <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          {serverGroup.createdTime && (
            <>
              <dt>Created</dt>
              <dd>{timestamp(serverGroup.createdTime)}</dd>
            </>
          )}
          <dt>Account</dt>
          <dd>
            <AccountTag account={sg.account} />
          </dd>
          <dt>Node</dt>
          <dd>{serverGroup.region}</dd>
          {sg.cluster && (
            <>
              <dt>Cluster</dt>
              <dd>{sg.cluster}</dd>
            </>
          )}
          {sg.application && (
            <>
              <dt>Application</dt>
              <dd>{sg.application}</dd>
            </>
          )}
          <dt>Status</dt>
          <dd>{serverGroup.isDisabled ? 'Disabled' : 'Enabled'}</dd>
        </dl>
      </CollapsibleSection>

      {Object.keys(lc).length > 0 && (
        <CollapsibleSection heading="VM Configuration" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            {lc.vmId != null && (
              <>
                <dt>VM ID</dt>
                <dd>{lc.vmId}</dd>
              </>
            )}
            {lc.status && (
              <>
                <dt>Status</dt>
                <dd>{lc.status}</dd>
              </>
            )}
            {lc.cpus != null && (
              <>
                <dt>vCPUs</dt>
                <dd>
                  {lc.cpus}
                  {lc.cores != null ? ` (${lc.sockets ?? 1} socket × ${lc.cores} cores)` : ''}
                </dd>
              </>
            )}
            {lc.memoryMb != null && (
              <>
                <dt>Memory</dt>
                <dd>{lc.memoryMb} MB</dd>
              </>
            )}
            {lc.diskGb != null && (
              <>
                <dt>Disk</dt>
                <dd>{lc.diskGb} GB</dd>
              </>
            )}
            {lc.osType && (
              <>
                <dt>OS Type</dt>
                <dd>{lc.osType}</dd>
              </>
            )}
            {lc.machine && (
              <>
                <dt>Machine</dt>
                <dd>{lc.machine}</dd>
              </>
            )}
            {lc.bios && (
              <>
                <dt>BIOS</dt>
                <dd>{lc.bios}</dd>
              </>
            )}
          </dl>
        </CollapsibleSection>
      )}

      <CollapsibleSection heading="Capacity" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Min</dt>
          <dd>{capacity.min ?? 0}</dd>
          <dt>Max</dt>
          <dd>{capacity.max ?? 0}</dd>
          <dt>Desired</dt>
          <dd>{capacity.desired ?? 0}</dd>
        </dl>
      </CollapsibleSection>

      <CollapsibleSection heading="Health" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Instances</dt>
          <dd>
            <HealthCounts container={serverGroup.instanceCounts} />
          </dd>
        </dl>
      </CollapsibleSection>

      {serverGroup.instances?.length > 0 && (
        <CollapsibleSection heading="Instances" defaultExpanded={true}>
          <table className="table table-condensed packed">
            <thead>
              <tr>
                <th>Name</th>
                <th>Node</th>
                <th>Health</th>
              </tr>
            </thead>
            <tbody>
              {(serverGroup.instances as IInstance[]).map((instance) => (
                <tr key={instance.id}>
                  <td>{instance.id}</td>
                  <td>{(instance as any).availabilityZone}</td>
                  <td>
                    <span className={`health-status-${instance.healthState}`}>{instance.healthState}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CollapsibleSection>
      )}
    </>
  );
}
