import React from 'react';

import { Application, ConfirmationModalService } from '@spinnaker/core';

import { StatefulMIGService } from './StatefulMIGService';
import { IGceServerGroup } from '../../../domain';

interface IMarkDiskStatefulButtonProps {
  application: Application;
  deviceName: string;
  serverGroup: IGceServerGroup;
}

export function MarkDiskStatefulButton({ application, deviceName, serverGroup }: IMarkDiskStatefulButtonProps) {
  function openConfirmationModal(): void {
    ConfirmationModalService.confirm({
      account: serverGroup.account,
      askForReason: true,
      buttonText: 'Mark as stateful',
      header: `Really mark disk ${deviceName} as stateful?`,
      submitMethod: () => {
        return StatefulMIGService.markDiskStateful(application.name, deviceName, serverGroup);
      },
      taskMonitorConfig: {
        application,
        title: 'Marking disk as stateful',
      },
    });
  }

  if (StatefulMIGService.isDiskStateful(deviceName, serverGroup)) {
    return <span> (Marked as Stateful)</span>;
  }

  return (
    <button className="btn-link" onClick={() => openConfirmationModal()}>
      Mark as Stateful
    </button>
  );
}
