import { module } from 'angular';
import { get, last } from 'lodash';
import React from 'react';
import { react2angular } from 'react2angular';

import { Application, withErrorBoundary } from '@spinnaker/core';

import { IGceDisk, IGceServerGroup } from '../../domain';
import { MarkDiskStatefulButton } from './stateful/MarkDiskStatefulButton';
import { StatefulMIGService } from './stateful/StatefulMIGService';
import { UpdateBootImageButton } from './stateful/UpdateBootImageButton';

interface IServerGroupDiskDescriptionProps {
  application: Application;
  serverGroup: IGceServerGroup;
}

class ServerGroupDiskDescriptions extends React.Component<IServerGroupDiskDescriptionProps> {
  public render() {
    const { application, serverGroup } = this.props;
    const disks: IGceDisk[] = get(serverGroup, 'launchConfig.instanceTemplate.properties.disks', []);
    const statefulOperationsEnabled: boolean = StatefulMIGService.statefulMigsEnabled();
    const canUpdateBootImage =
      statefulOperationsEnabled &&
      disks.some((disk) => StatefulMIGService.isDiskStateful(disk.deviceName, serverGroup));

    return disks.map((disk) => {
      if (disk.boot) {
        return (
          <React.Fragment key={disk.deviceName}>
            <dt>
              Boot Disk
              {canUpdateBootImage && (
                <UpdateBootImageButton
                  application={application}
                  bootImage={ServerGroupDiskDescriptions.getDiskImageName(disk)}
                  serverGroup={serverGroup}
                />
              )}
            </dt>
            <dd>{ServerGroupDiskDescriptions.getDiskTypeLabel(disk)}</dd>
            <dd>{ServerGroupDiskDescriptions.getDiskImageLabel(disk)}</dd>
          </React.Fragment>
        );
      }
      return (
        <React.Fragment key={disk.deviceName}>
          <dt>
            Disk
            {statefulOperationsEnabled && (
              <MarkDiskStatefulButton
                application={application}
                deviceName={disk.deviceName}
                serverGroup={serverGroup}
              />
            )}
          </dt>
          <dd>{ServerGroupDiskDescriptions.getDiskTypeLabel(disk)}</dd>
          <dd>{ServerGroupDiskDescriptions.getDiskImageLabel(disk)}</dd>
        </React.Fragment>
      );
    });
  }

  private static translateDiskType = (disk: IGceDisk): string => {
    const diskType = disk.initializeParams.diskType;
    if (diskType === 'pd-ssd') {
      return 'Persistent SSD';
    } else if (diskType === 'local-ssd') {
      return 'Local SSD';
    } else {
      return 'Persistent Std';
    }
  };

  private static getDiskTypeLabel = (disk: IGceDisk): string => {
    return `${ServerGroupDiskDescriptions.translateDiskType(disk)}: ${disk.initializeParams.diskSizeGb}GB`;
  };

  private static getDiskImageLabel = (disk: IGceDisk): string => {
    return `Image: ${ServerGroupDiskDescriptions.getDiskImageName(disk)}`;
  };

  private static getDiskImageName = (disk: IGceDisk): string => {
    return last(get(disk, 'initializeParams.sourceImage', '').split('/'));
  };
}

export const GCE_SERVER_GROUP_DISK_DESCRIPTIONS = 'spinnaker.gce.serverGroupDiskDescriptions';
module(GCE_SERVER_GROUP_DISK_DESCRIPTIONS, []).component(
  'gceServerGroupDiskDescriptions',
  react2angular(withErrorBoundary(ServerGroupDiskDescriptions, 'gceServerGroupDiskDescriptions'), [
    'application',
    'serverGroup',
  ]),
);
