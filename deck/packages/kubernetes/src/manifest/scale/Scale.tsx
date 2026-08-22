import React from 'react';
import { MenuItem } from 'react-bootstrap';

import type { Application } from '@spinnaker/core';
import { useModal } from '@spinnaker/core';

import { ScaleModal } from './ScaleModal';
import type { IAnyKubernetesResource } from '../../interfaces';

export interface IScaleProps {
  application: Application;
  resource: IAnyKubernetesResource;
  currentReplicas: number;
}

export function Scale({ application, resource, currentReplicas }: IScaleProps) {
  const { open, show, close } = useModal();
  const handleClick = () => show();
  return (
    <>
      <MenuItem onClick={handleClick}>Scale</MenuItem>
      <ScaleModal
        isOpen={open}
        dismissModal={close}
        application={application}
        coordinates={{
          name: resource.name,
          namespace: resource.namespace,
          account: resource.account,
        }}
        currentReplicas={currentReplicas}
      />
    </>
  );
}
