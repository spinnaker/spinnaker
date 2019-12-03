import React from 'react';

import styles from './ModalBody.module.css';

export interface IModalBodyProps {
  children?: React.ReactNode;
}

export const ModalBody = ({ children }: IModalBodyProps) => <div className={styles.body}>{children}</div>;
