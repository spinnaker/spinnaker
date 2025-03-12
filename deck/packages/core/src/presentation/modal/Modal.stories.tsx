import { Button } from '@storybook/react/demo';
import React from 'react';

import { Modal, ModalBody, ModalHeader, showModal } from './index';

export default { component: Modal, title: 'Modal' };

const ModalWithoutFooterComponent = () => {
  return (
    <>
      <ModalHeader>Modal</ModalHeader>
      <ModalBody>
        <div className="flex-container-h middle center" style={{ width: '100%', height: '100%' }}>
          <span className="heading-1">Simple modal without footer</span>
        </div>
      </ModalBody>
    </>
  );
};
export const ModalWithoutFooter = () => {
  return (
    <div className="sp-margin-xl">
      <Button onClick={() => showModal(ModalWithoutFooterComponent)}>Show Modal</Button>
    </div>
  );
};
