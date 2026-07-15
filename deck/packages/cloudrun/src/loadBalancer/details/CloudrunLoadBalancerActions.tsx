import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type { ILoadBalancerActionsProps, ILoadBalancerDeleteCommand } from '@spinnaker/core';
import { ConfirmationModalService, LoadBalancerWriter } from '@spinnaker/core';

import type { ICloudrunLoadBalancer } from '../../common/domain';
import { CloudrunLoadBalancerModal } from '../configure/wizard/CloudrunLoadBalancerModal';

export function CloudrunLoadBalancerActions({ app, loadBalancer }: ILoadBalancerActionsProps) {
  const cloudrunLoadBalancer = loadBalancer as ICloudrunLoadBalancer;
  const canDeleteLoadBalancer = cloudrunLoadBalancer.name !== 'default';

  const editLoadBalancer = () => {
    CloudrunLoadBalancerModal.show({ app, loadBalancer: cloudrunLoadBalancer, isNew: false, forPipelineConfig: false });
  };
  const getConfirmationModalBodyHtml = (): string => {
    const serverGroupNames = cloudrunLoadBalancer.serverGroups.map((serverGroup) => serverGroup.name);
    if (!serverGroupNames?.length) {
      return null;
    }
    if (serverGroupNames.length > 1) {
      const listOfServerGroupNames = serverGroupNames.map((name) => `<li>${name}</li>`).join('');
      return `<div class="alert alert-warning"><p>Deleting <b>${cloudrunLoadBalancer.name}</b> will destroy the following server groups:<ul>${listOfServerGroupNames}</ul></p></div>`;
    }
    return `<div class="alert alert-warning"><p>Deleting <b>${cloudrunLoadBalancer.name}</b> will destroy <b>${serverGroupNames[0]}</b>.</p></div>`;
  };
  const deleteLoadBalancer = () => {
    const submitMethod = () => {
      const command: ILoadBalancerDeleteCommand = {
        cloudProvider: cloudrunLoadBalancer.cloudProvider,
        loadBalancerName: cloudrunLoadBalancer.name,
        credentials: cloudrunLoadBalancer.account,
      };
      return LoadBalancerWriter.deleteLoadBalancer(command, app);
    };

    ConfirmationModalService.confirm({
      header: `Really delete ${cloudrunLoadBalancer.name}?`,
      buttonText: `Delete ${cloudrunLoadBalancer.name}`,
      body: getConfirmationModalBodyHtml(),
      account: cloudrunLoadBalancer.account,
      taskMonitorConfig: {
        application: app,
        title: `Deleting ${cloudrunLoadBalancer.name}`,
      },
      submitMethod,
    });
  };

  return (
    <Dropdown className="dropdown" id="cloudrun-load-balancer-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Load Balancer Actions</Dropdown.Toggle>
      <Dropdown.Menu>
        <MenuItem onClick={editLoadBalancer}>Edit Load Balancer</MenuItem>
        {canDeleteLoadBalancer ? (
          <MenuItem onClick={deleteLoadBalancer}>Delete Load Balancer</MenuItem>
        ) : (
          <MenuItem disabled={true} title="You cannot delete a default service.">
            Delete Load Balancer
          </MenuItem>
        )}
      </Dropdown.Menu>
    </Dropdown>
  );
}
