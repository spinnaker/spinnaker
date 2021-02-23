import { isNumber } from 'lodash';
import React, { useMemo } from 'react';
import ReactDOM from 'react-dom';
import { CSSTransition } from 'react-transition-group';

import { ModalContext } from './ModalContext';
import { TabBoundary } from '../TabBoundary';
import { useContainerClassNames, useEventListener, useLatestCallback } from '../hooks';

import './Modal.less';

const DEFAULT_MAX_WIDTH = '1400px';

export interface IModalProps {
  isOpen: boolean;
  maxWidth?: number | string;
  onRequestClose?: () => any;
  onAfterClose?: () => any;
  children?: React.ReactNode;
}

export const Modal = ({ isOpen, maxWidth, onRequestClose, onAfterClose, children }: IModalProps) => {
  useContainerClassNames(isOpen ? ['sp-modal-backdrop-blur-effect'] : []);

  const keydownCallback = ({ keyCode }: KeyboardEvent) => {
    if (keyCode === 27 /* esc */) {
      onRequestClose();
    }
  };
  useEventListener(document, 'keydown', isOpen ? keydownCallback : null);

  const memoizedRequestClose = useLatestCallback(onRequestClose);
  const modalContext = useMemo(() => ({ onRequestClose: memoizedRequestClose }), []);

  return (
    <ModalContext.Provider value={modalContext}>
      {ReactDOM.createPortal(
        <TabBoundary>
          <CSSTransition in={isOpen} timeout={300} mountOnEnter={true} unmountOnExit={true}>
            <div className="sp-modal-backdrop" />
          </CSSTransition>
          <CSSTransition in={isOpen} timeout={300} mountOnEnter={true} unmountOnExit={true} onExited={onAfterClose}>
            <div className="Modal">
              <div
                className="sp-dialog-sizer"
                style={{
                  maxWidth: (isNumber(maxWidth) ? `${maxWidth}px` : maxWidth) ?? DEFAULT_MAX_WIDTH,
                }}
              >
                <div className="sp-dialog-content">{children}</div>
              </div>
            </div>
          </CSSTransition>
        </TabBoundary>,
        document.body,
      )}
    </ModalContext.Provider>
  );
};
