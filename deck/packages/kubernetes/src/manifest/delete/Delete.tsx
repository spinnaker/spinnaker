import { module } from 'angular';
import React from 'react';
import { MenuItem } from 'react-bootstrap';
import { react2angular } from 'react2angular';

import type { Application } from '@spinnaker/core';
import { withErrorBoundary } from '@spinnaker/core';
import { useModal } from '@spinnaker/core';

import { DeleteModal } from './DeleteModal';
import type { IAnyKubernetesResource } from '../../interfaces';

export interface IDeleteProps {
  application: Application;
  resource: IAnyKubernetesResource;
  manifestController: string | undefined;
}

export function Delete(props: IDeleteProps) {
  const { open, show, close } = useModal();
  const handleClick = () => show();
  return (
    <>
      <MenuItem onClick={handleClick}>Delete</MenuItem>
      <DeleteModal isOpen={open} dismissModal={close} {...props} />
    </>
  );
}

export const KUBERNETES_DELETE = 'spinnaker.kubernetes.delete';
module(KUBERNETES_DELETE, []).component(
  'kubernetesDelete',
  react2angular(withErrorBoundary(Delete, 'kubernetesDelete'), ['application', 'resource', 'manifestController']),
);
