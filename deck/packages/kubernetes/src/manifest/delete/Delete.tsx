import React from 'react';
import { MenuItem } from 'react-bootstrap';

import type { Application } from '@spinnaker/core';
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
