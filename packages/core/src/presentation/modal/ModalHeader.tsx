import cx from 'classnames';
import React from 'react';

import { ModalContext } from './ModalContext';

import './ModalHeader.less';

const { useContext } = React;

export interface IModalHeaderProps {
  children?: React.ReactNode;
  className?: string;
}

export const ModalHeader = ({ className, children }: IModalHeaderProps) => {
  const { onRequestClose } = useContext(ModalContext);

  return (
    <div className="ModalHeader">
      <div className={cx('sp-modal-title', className)}>{children}</div>
      <button className="sp-modal-close" onClick={() => onRequestClose && onRequestClose()}>
        <i className="ico icon-close" />
      </button>
    </div>
  );
};
