import * as React from 'react';
import { Modal, ModalProps } from 'react-bootstrap';
import * as ReactDOM from 'react-dom';

import { pick, omit } from 'lodash';

/** The Modal content Component will be passed these two props */
export interface IModalComponentProps {
  // Close modal with a result value (i.e., OK button)
  closeModal?(result: any): void;

  // Dismiss/reject modal (i.e., Cancel button)
  dismissModal?(rejectReason: any): void;
}

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
   * @param props to pass to the Modal and ModalComponent
   * @returns {Promise<T>}
   */
  public static show<P extends IModalComponentProps, T = any>(ModalComponent: React.ComponentType<P>, props?: P): Promise<T> {
    const modalPropKeys: [keyof ModalProps] = [
      'onHide', 'animation', 'autoFocus', 'backdrop', 'backdropClassName', 'backdropStyle',
      'backdropTransitionTimeout', 'bsSize', 'container', 'containerClassName', 'dialogClassName',
      'dialogComponent', 'dialogTransitionTimeout', 'enforceFocus', 'keyboard', 'onBackdropClick',
      'onEnter', 'onEntered', 'onEntering', 'onEscapeKeyUp', 'onExit', 'onExited', 'onExiting',
      'onShow', 'transition',
    ];

    const modalProps: ModalProps = pick(props, modalPropKeys);
    const componentProps = omit(props, modalPropKeys);

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
        resultHandler(result);
        // Use react-bootstrap modal lifecycle, i.e. show=false, which triggers onExited
        show = false;
        render();
      };

      const handleClose = destroy(resolve);
      const handleDismiss = destroy(reject);

      function render() {
        ReactDOM.render((
            <Modal show={show} {...modalProps} onExited={onExited}>
              <ModalComponent {...componentProps} dismissModal={handleDismiss} closeModal={handleClose}/>
            </Modal>
          ),
          mountNode
        );
      }

      render();
    });

    modalPromise.catch(() => {});

    return modalPromise;
  }
}
