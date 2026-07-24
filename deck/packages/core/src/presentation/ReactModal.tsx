import { UIRouterContext } from '@uirouter/react';
import React from 'react';
import type { ModalProps } from 'react-bootstrap';
import { Modal } from 'react-bootstrap';
import ReactDOM from 'react-dom';

import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import type { DeckRuntimeServices } from '../bootstrap/DeckRuntimeServices';
import type { IModalComponentProps } from './modal';
import { getDirectRouter } from '../navigation/directRouter';

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
   * @param runtimeServices to provide to modal components that use Deck runtime services
   * @returns {Promise<T>}
   */
  public static show<P extends IModalComponentProps, T = any>(
    ModalComponent: React.ComponentType<P>,
    componentProps?: P,
    modalProps?: Partial<ModalProps>,
    runtimeServices?: DeckRuntimeServices,
  ): Promise<T> {
    const modalPromise = new Promise<T>((resolve, reject) => {
      let mountNode = document.createElement('div');
      let show = true;
      let pendingResult: { handler: (result: any) => void; value: any } | null = null;

      function onExited() {
        if (!mountNode) {
          return;
        }

        if (pendingResult) {
          const { handler, value } = pendingResult;
          pendingResult = null;
          handler(value);
        }

        ReactDOM.unmountComponentAtNode(mountNode);
        mountNode = null;
      }

      const destroy = (resultHandler: (result: any) => void) => (result: any) => {
        if (!mountNode) {
          return;
        }

        if (pendingResult) {
          return;
        }

        pendingResult = { handler: resultHandler, value: result };
        show = false;
        render();
      };

      const handleClose = destroy(resolve);
      const handleDismiss = destroy(reject);

      function render() {
        const router = getDirectRouter();
        const modal = (
          <Modal show={show} {...(modalProps as ModalProps)} onExited={onExited}>
            <ModalComponent {...componentProps} dismissModal={handleDismiss} closeModal={handleClose} />
          </Modal>
        );
        const runtimeModal = runtimeServices ? (
          <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>{modal}</DeckRuntimeContext.Provider>
        ) : (
          modal
        );
        ReactDOM.render(
          router ? <UIRouterContext.Provider value={router}>{runtimeModal}</UIRouterContext.Provider> : runtimeModal,
          mountNode,
        );
      }

      render();
    });

    modalPromise.catch(() => {});

    return modalPromise;
  }
}
