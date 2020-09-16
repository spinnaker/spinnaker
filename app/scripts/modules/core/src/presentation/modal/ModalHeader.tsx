import React from 'react';

const { useContext } = React;

import { ModalContext } from './ModalContext';

import './ModalHeader.less';

export interface IModalHeaderProps {
  children?: React.ReactNode;
}

export const ModalHeader = ({ children }: IModalHeaderProps) => {
  const { onRequestClose } = useContext(ModalContext);

  return (
    <div className="ModalHeader">
      <div className="sp-modal-title">{children}</div>
      <button className="sp-modal-close" onClick={() => onRequestClose && onRequestClose()}>
        <i className="ico icon-close" />
      </button>
    </div>
  );
};
