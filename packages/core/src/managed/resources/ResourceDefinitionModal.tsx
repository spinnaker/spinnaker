import React from 'react';

import type { QueryResource } from '../overview/types';
import type { IModalComponentProps } from '../../presentation/modal';
import { ModalBody, ModalHeader, showModal } from '../../presentation/modal';
import { YamlViewer } from '../utils/YamlViewer';

import './ResourceDefinitionModal.less';

export type IResourceDefinitionModalProps = IModalComponentProps & { resource: QueryResource };

export const showResourceDefinitionModal = (props: IResourceDefinitionModalProps) =>
  showModal(ResourceDefinitionModal, props);

export const ResourceDefinitionModal = ({ resource }: IResourceDefinitionModalProps) => {
  if (!resource.rawDefinition) return null;
  return (
    <div className="ResourceDefinitionModal">
      <ModalHeader>
        Resource definition - {resource.displayName}
        <div className="modal-subtitle">(Includes resolved fields and metadata added by the system)</div>
      </ModalHeader>
      <ModalBody>
        <YamlViewer content={resource.rawDefinition} />
      </ModalBody>
    </div>
  );
};
