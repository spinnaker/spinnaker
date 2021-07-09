import React from 'react';
import { Modal, ModalProps } from 'react-bootstrap';
import ReactDOM from 'react-dom';

import { IModalComponentProps } from './modal';

/** An imperative service for showing a react component as a modal */
export class ReactModal {
  /**
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
   * ModalService.show<string>(MyComponent).then(result => {
   *   this.setState({ result });
   * });
   *
   * @param ModalComponent the component to be rendered inside a modal
   * @param componentProps to pass to the ModalComponent
   * @param modalProps to pass to the Modal
   * @returns {Promise<T>}
   */
  public static show<P extends IModalComponentProps, T = any>(
    ModalComponent: React.ComponentType<P>,
    componentProps?: P,
    modalProps?: Partial<ModalProps>,
  ): Promise<T> {
    const modalPromise = new Promise<T>((resolve, reject) => {
      let mountNode = document.createElement('div');
      let show = true;

      function onExited() {
        if (!mountNode) {
          return;
        }

        ReactDOM.unmountComponentAtNode(mountNode);
        mountNode = null;
      }

      const destroy = (resultHandler: (result: any) => void) => (result: any) => {
        if (!mountNode) {
          return;
        }

        resultHandler(result);
        // Use react-bootstrap modal lifecycle, i.e. show=false, which triggers onExited
        show = false;
        render();
      };

      const handleClose = destroy(resolve);
      const handleDismiss = destroy(reject);

      function render() {
        ReactDOM.render(
          <Modal show={show} {...(modalProps as ModalProps)} onExited={onExited}>
            <ModalComponent {...componentProps} dismissModal={handleDismiss} closeModal={handleClose} />
          </Modal>,
          mountNode,
        );
      }

      render();
    });

    modalPromise.catch(() => {});

    return modalPromise;
  }
}
