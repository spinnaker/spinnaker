import { module } from 'angular';
import React from 'react';
import { MenuItem } from 'react-bootstrap';
import { react2angular } from 'react2angular';

import type { Application } from '@spinnaker/core';
import { withErrorBoundary } from '@spinnaker/core';
import { useModal } from '@spinnaker/core';

import { ScaleModal } from './ScaleModal';
import type { IAnyKubernetesResource } from '../../interfaces';

export interface IScaleProps {
  application: Application;
  resource: IAnyKubernetesResource;
  currentReplicas: number;
}

export function Scale(props: IScaleProps) {
  const { open, show, close } = useModal();
  const handleClick = () => show();
  return (
    <>
      <MenuItem onClick={handleClick}>Scale</MenuItem>
      <ScaleModal isOpen={open} dismissModal={close} {...props} />
    </>
  );
}

export const KUBERNETES_SCALE = 'spinnaker.kubernetes.scale';
module(KUBERNETES_SCALE, []).component(
  'kubernetesScale',
  react2angular(withErrorBoundary(Scale, 'kubernetesScale'), ['application', 'resource', 'currentReplicas']),
);
