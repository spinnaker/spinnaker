import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type { ILoadBalancerActionsProps } from '@spinnaker/core';
import { AddEntityTagLinks, robotToHuman, SETTINGS, useModal } from '@spinnaker/core';

import type { IKubernetesLoadBalancerView } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest';
import { DeleteModal } from '../../manifest/delete/DeleteModal';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

export interface IKubernetesLoadBalancerActionsProps extends ILoadBalancerActionsProps {
  loadBalancer: IKubernetesLoadBalancerView;
}

export function KubernetesLoadBalancerActions({ app, loadBalancer }: IKubernetesLoadBalancerActionsProps) {
  const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
  const isEnabled = SETTINGS.kubernetesAdHocInfraWritesEnabled;
  const { manifest } = loadBalancer;
  const deleteModal = useModal();

  const editLoadBalancer = (): void => {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      app,
      manifest.manifest,
      loadBalancer.moniker,
      loadBalancer.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: app, command: builtCommand });
    });
  };

  return (
    <>
      <Dropdown className="dropdown" id="load-balancer-actions-dropdown">
        {isEnabled && (
          <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
            {robotToHuman(loadBalancer.kind)} Actions
          </Dropdown.Toggle>
        )}
        <Dropdown.Menu>
          <MenuItem onClick={deleteModal.show}>Delete</MenuItem>
          <MenuItem onClick={editLoadBalancer}>Edit</MenuItem>
          {showEntityTags && (
            <AddEntityTagLinks
              component={loadBalancer}
              application={app}
              entityType="loadBalancer"
              onUpdate={() => app.loadBalancers.refresh()}
            />
          )}
        </Dropdown.Menu>
      </Dropdown>
      <DeleteModal
        application={app}
        resource={loadBalancer}
        manifestController={null}
        isOpen={deleteModal.open}
        dismissModal={deleteModal.close}
      />
    </>
  );
}
