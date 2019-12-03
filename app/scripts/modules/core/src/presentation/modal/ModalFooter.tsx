import React from 'react';

import styles from './ModalFooter.module.css';

export interface IModalFooterProps {
  primaryActions?: React.ReactNode;
  secondaryActions?: React.ReactNode;
}

export const ModalFooter = ({ primaryActions, secondaryActions }: IModalFooterProps) => (
  <div className={styles.footer}>
    {secondaryActions && <div className={styles.footerLeft}>{secondaryActions}</div>}
    {primaryActions && <div className={styles.footerRight}>{primaryActions}</div>}
  </div>
);
