import React from 'react';

import type { Application } from '@spinnaker/core';

import type { IGceDisk, IGceServerGroup } from '../../domain';
import { GCEProviderSettings } from '../../gce.settings';
import { MarkDiskStatefulButton } from './stateful/MarkDiskStatefulButton';
import { StatefulMIGService } from './stateful/StatefulMIGService';
import { UpdateBootImageButton } from './stateful/UpdateBootImageButton';

export interface IGceServerGroupDiskDescriptionsProps {
  application: Application;
  serverGroup: IGceServerGroup;
}

function translateDiskType(disk: IGceDisk): string {
  const diskType = disk.initializeParams?.diskType;
  if (diskType === 'pd-ssd') {
    return 'Persistent SSD';
  } else if (diskType === 'local-ssd') {
    return 'Local SSD';
  }
  return 'Persistent Std';
}

export function getGceDiskTypeLabel(disk: IGceDisk): string {
  return `${translateDiskType(disk)}: ${disk.initializeParams?.diskSizeGb}GB`;
}

export function getGceDiskImageName(disk: IGceDisk): string {
  return (disk.initializeParams?.sourceImage || '').split('/').pop();
}

function statefulMigsEnabled(): boolean {
  return Boolean(GCEProviderSettings?.feature?.statefulMigsEnabled);
}

/**
 * Renders `<dt>`/`<dd>` rows (no wrapping `<dl>`) describing the boot and additional disks of a server group's
 * instance template, intended to be placed inside the Launch Configuration definition list.
 */
export function GceServerGroupDiskDescriptions({
  application,
  serverGroup,
}: IGceServerGroupDiskDescriptionsProps): JSX.Element {
  const disks: IGceDisk[] = (serverGroup as any)?.launchConfig?.instanceTemplate?.properties?.disks || [];
  const statefulOperationsEnabled = statefulMigsEnabled();
  const canUpdateBootImage =
    statefulOperationsEnabled && disks.some((disk) => StatefulMIGService.isDiskStateful(disk.deviceName, serverGroup));

  return (
    <>
      {disks.map((disk, index) => (
        <React.Fragment key={disk.deviceName || index}>
          <dt>
            {disk.boot ? 'Boot Disk' : 'Disk'}
            {disk.boot && canUpdateBootImage && (
              <UpdateBootImageButton
                application={application}
                bootImage={getGceDiskImageName(disk)}
                serverGroup={serverGroup}
              />
            )}
            {!disk.boot && statefulOperationsEnabled && (
              <MarkDiskStatefulButton
                application={application}
                deviceName={disk.deviceName}
                serverGroup={serverGroup}
              />
            )}
          </dt>
          <dd>{getGceDiskTypeLabel(disk)}</dd>
          {getGceDiskImageName(disk) && <dd>Image: {getGceDiskImageName(disk)}</dd>}
        </React.Fragment>
      ))}
    </>
  );
}
