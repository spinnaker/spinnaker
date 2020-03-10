import React from 'react';
import ReactDOM from 'react-dom';
import { CSSTransition } from 'react-transition-group';

const { useMemo } = React;

import { useLatestCallback, useContainerClassNames, useEventListener } from '../hooks';
import { TabBoundary } from '../TabBoundary';

import { ModalContext } from './ModalContext';
import styles from './Modal.module.css';

export interface IModalProps {
  isOpen: boolean;
  onRequestClose?: () => any;
  onAfterClose?: () => any;
  children?: React.ReactNode;
}

export const Modal = ({ onRequestClose, onAfterClose, isOpen, children }: IModalProps) => {
  useContainerClassNames(isOpen ? [styles.backdropBlurEffect] : []);

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
          <CSSTransition in={isOpen} timeout={300} mountOnEnter={true} unmountOnExit={true} classNames={styles}>
            <div className={styles.backdrop} />
          </CSSTransition>
          <CSSTransition
            in={isOpen}
            timeout={300}
            mountOnEnter={true}
            unmountOnExit={true}
            classNames={styles}
            onExited={onAfterClose}
          >
            <div className={styles.dialogWrapper}>
              <div className={styles.dialog}>{children}</div>
            </div>
          </CSSTransition>
        </TabBoundary>,
        document.body,
      )}
    </ModalContext.Provider>
  );
};
