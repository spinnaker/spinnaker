import { module } from 'angular';
import React from 'react';
import { MenuItem } from 'react-bootstrap';
import { react2angular } from 'react2angular';

import type { Application } from '@spinnaker/core';
import { withErrorBoundary } from '@spinnaker/core';
import { useModal } from '@spinnaker/core';

import { UndoRolloutModal } from './UndoRolloutModal';
import type { IAnyKubernetesResource } from '../../interfaces';

export interface IUndoRolloutProps {
  application: Application;
  resource: IAnyKubernetesResource;
}

export function UndoRollout(props: IUndoRolloutProps) {
  const { open, show, close } = useModal();
  const handleClick = () => show();
  return (
    <>
      <MenuItem onClick={handleClick}>Undo Rolloutaaaa</MenuItem>
      <UndoRolloutModal isOpen={open} dismissModal={close} {...props} />
    </>
  );
}

export const KUBERNETES_UNDO_ROLLOUT = 'spinnaker.kubernetes.undo.rollout';
module(KUBERNETES_UNDO_ROLLOUT, []).component(
  'kubernetesUndoRollout',
  react2angular(withErrorBoundary(UndoRollout, 'kubernetesUndoRollout'), ['application', 'resource']),
);
