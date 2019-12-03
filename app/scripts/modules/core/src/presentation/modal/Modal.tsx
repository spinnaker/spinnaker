import React from 'react';
import ReactDOM from 'react-dom';
import { CSSTransition } from 'react-transition-group';

const { useMemo } = React;

import { useEventListener, useContainerClassNames, useLatestCallback } from 'core/presentation';

import { ModalContext } from './ModalContext';
import styles from './Modal.module.css';

export interface IModalProps {
  isOpen: boolean;
  onRequestClose?: () => any;
  children?: React.ReactNode;
}

export const Modal = ({ onRequestClose, isOpen, children }: IModalProps) => {
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
        <>
          <CSSTransition in={isOpen} timeout={300} mountOnEnter={true} unmountOnExit={true} classNames={styles}>
            <div className={styles.backdrop} />
          </CSSTransition>
          <CSSTransition in={isOpen} timeout={300} mountOnEnter={true} unmountOnExit={true} classNames={styles}>
            <div className={styles.dialogWrapper}>
              <div className={styles.dialog}>{children}</div>
            </div>
          </CSSTransition>
        </>,
        document.body,
      )}
    </ModalContext.Provider>
  );
};
