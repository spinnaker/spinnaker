import React from 'react';
import { MenuItem } from 'react-bootstrap';

import type { Application } from '@spinnaker/core';
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
      <MenuItem onClick={handleClick}>Undo Rollout</MenuItem>
      <UndoRolloutModal isOpen={open} dismissModal={close} {...props} />
    </>
  );
}
