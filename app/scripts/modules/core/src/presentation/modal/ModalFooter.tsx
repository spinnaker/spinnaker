import React from 'react';

import './ModalFooter.less';

export interface IModalFooterProps {
  primaryActions?: React.ReactNode;
  secondaryActions?: React.ReactNode;
}

export const ModalFooter = ({ primaryActions, secondaryActions }: IModalFooterProps) => (
  <div className="ModalFooter">
    {secondaryActions && <div className="sp-modal-footer-left">{secondaryActions}</div>}
    {primaryActions && <div className="sp-modal-footer-right">{primaryActions}</div>}
  </div>
);
