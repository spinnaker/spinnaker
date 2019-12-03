import React from 'react';

const { useContext } = React;

import { ModalContext } from './ModalContext';
import styles from './ModalHeader.module.css';

export interface IModalHeaderProps {
  children?: React.ReactNode;
}

export const ModalHeader = ({ children }: IModalHeaderProps) => {
  const { onRequestClose } = useContext(ModalContext);

  return (
    <div className={styles.header}>
      <div className={styles.title}>{children}</div>
      <div className={styles.close} onClick={() => onRequestClose && onRequestClose()}>
        <i className="ico icon-close" />
      </div>
    </div>
  );
};
