import React from 'react';

import './ModalBody.less';

export interface IModalBodyProps {
  children?: React.ReactNode;
}

export const ModalBody = ({ children }: IModalBodyProps) => <div className="ModalBody">{children}</div>;
