import React from 'react';

export interface IModalContext {
  onRequestClose: () => any;
}

export const ModalContext = React.createContext<IModalContext>(null);
