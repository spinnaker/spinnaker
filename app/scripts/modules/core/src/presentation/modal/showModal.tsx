import { UIRouterContextComponent } from '@uirouter/react-hybrid';
import React from 'react';
import ReactDOM from 'react-dom';

import { IModalProps, Modal } from './Modal';

/** The Modal content Component will be passed these two props */
export interface IModalComponentProps<C = any, D = any> {
  // Close modal with a result value (i.e., OK button)
  closeModal?(result?: C): void;

  // Dismiss/reject modal (i.e., Cancel button)
  dismissModal?(result?: D): void;
}

interface ModalCloseResult<T> {
  status: 'CLOSED';
  closeResult: T;
}

interface ModalDismissResult<T> {
  status: 'DISMISSED';
  dismissResult?: T;
}

export type IModalResult<C, D> = ModalCloseResult<C> | ModalDismissResult<D>;

/**
 * An imperative API for showing a react component as a modal.
 *
 * example:
 * const MyComponent = ({ closeModal, dismissModal }) => {
 *   <h1>Modal Contents!</h1>
 *   <button onClick={() => closeModal('A')}>Choice A</button>
 *   <button onClick={() => closeModal('B')}>Choice B</button>
 *   <button onClick={() => dismissModal('cancelled')}>Cancel</button>
 * }
 *
 * ...
 *
 * showModal(MyComponent).then(result => {
 *   this.setState({ result });
 * });
 *
 * @param ModalComponent the component to be rendered inside a modal
 * @param componentProps to pass to the ModalComponent
 * @param modalProps props to pass to the modal itself
 * @returns {Promise<IModalResult<C, D>}
 */
export const showModal = <P, C = any, D = any>(
  ModalComponent: React.ComponentType<P & IModalComponentProps<C, D>>,
  componentProps?: P,
  modalProps?: Omit<IModalProps, 'isOpen' | 'onRequestClose' | 'onAfterClose' | 'children'>,
): Promise<IModalResult<C, D>> =>
  new Promise<IModalResult<C, D>>((resolve) => {
    let mountNode = document.createElement('div');
    let show = false;

    function onAfterClose() {
      if (!mountNode) {
        return;
      }

      ReactDOM.unmountComponentAtNode(mountNode);
      mountNode = null;
    }

    const handleResultWith = (resultHandler: (result: C | D) => IModalResult<C, D>) => (result: C | D) => {
      if (!mountNode) {
        return;
      }

      resolve(resultHandler(result));
      // Switch `show` to false to trigger exit transition and call onExitComplete
      show = false;
      render();
    };

    const handleClose = handleResultWith((result: C) => ({ status: 'CLOSED', closeResult: result }));
    const handleDismiss = handleResultWith((result: D) => ({ status: 'DISMISSED', dismissResult: result }));
    const handleRequestClose = () => handleDismiss(null);

    function render() {
      ReactDOM.render(
        <Modal isOpen={show} onRequestClose={handleRequestClose} onAfterClose={onAfterClose} {...modalProps}>
          <UIRouterContextComponent>
            <ModalComponent {...componentProps} dismissModal={handleDismiss} closeModal={handleClose} />
          </UIRouterContextComponent>
        </Modal>,
        mountNode,
      );
    }

    // If the first render has show=true, the enter transition for the modal
    // will short-circuit because all the transition classes are added
    // immediately in a single paint.
    // Instead let's render once with show=false, let the browser paint,
    // then render a second time with show=true.
    render();

    setTimeout(() => {
      if (!mountNode) {
        return;
      }

      show = true;
      render();
    });
  });
