import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type { IRouterInjectedProps, IServerGroupActionsProps } from '@spinnaker/core';
import { ConfirmationModalService, useDeckRuntimeServices, withRouter } from '@spinnaker/core';

import { CloudrunHealth } from '../../common/cloudrunHealth';
import type { ICloudrunServerGroup } from '../../interfaces';

export function CloudrunServerGroupActionsComponent({
  app,
  serverGroup,
  stateService,
}: IServerGroupActionsProps & IRouterInjectedProps) {
  const { serverGroupWriter } = useDeckRuntimeServices();
  const cloudrunServerGroup = serverGroup as ICloudrunServerGroup;
  const canDestroyServerGroup = Boolean(
    cloudrunServerGroup && !cloudrunServerGroup.tags?.isLatest && cloudrunServerGroup.disabled,
  );
  const destroyServerGroup = () => {
    const stateParams = {
      name: cloudrunServerGroup.name,
      accountId: cloudrunServerGroup.account,
      region: cloudrunServerGroup.region,
    };
    const taskMonitor = {
      application: app,
      title: `Destroying ${cloudrunServerGroup.name}`,
      onTaskComplete: () => {
        if (stateService.includes('**.serverGroup', stateParams)) {
          stateService.go('^');
        }
      },
    };
    const confirmationModalParams = {
      header: `Really destroy ${cloudrunServerGroup.name}?`,
      buttonText: `Destroy ${cloudrunServerGroup.name}`,
      account: cloudrunServerGroup.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: (params: any) => serverGroupWriter.destroyServerGroup(cloudrunServerGroup, app, params),
      askForReason: true,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: CloudrunHealth.PLATFORM,
      interestingHealthProviderNames: [] as string[],
    };

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = [CloudrunHealth.PLATFORM];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  };

  return (
    <Dropdown className="dropdown" id="cloudrun-server-group-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Server Group Actions</Dropdown.Toggle>
      <Dropdown.Menu>
        {cloudrunServerGroup.tags?.isLatest && (
          <MenuItem
            disabled={true}
            title="You cannot destroy the latest server group (revision). You may be able to delete this server group's load balancer."
          >
            Destroy
          </MenuItem>
        )}
        {canDestroyServerGroup && <MenuItem onClick={destroyServerGroup}>Destroy</MenuItem>}
        {!canDestroyServerGroup && !cloudrunServerGroup.tags?.isLatest && (
          <MenuItem
            disabled={true}
            title="You cannot destroy this server group while it is receiving traffic. You may be able to delete this server group's load balancer."
          >
            Destroy
          </MenuItem>
        )}
      </Dropdown.Menu>
    </Dropdown>
  );
}

export const CloudrunServerGroupActions = withRouter(CloudrunServerGroupActionsComponent);
