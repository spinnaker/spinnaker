import { UIRouterContext } from '@uirouter/react';
import React from 'react';
import type { ModalProps } from 'react-bootstrap';
import { Modal } from 'react-bootstrap';
import ReactDOM from 'react-dom';

import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import type { DeckRuntimeServices } from '../bootstrap/DeckRuntimeServices';
import type { IModalComponentProps } from './modal';
import { getDirectRouter } from '../navigation/directRouter';
import { diagnosticLogger } from '../utils/diagnosticLogger';

/** An imperative service for showing a react component as a modal */
export class ReactModal {
  private static activeModals = new Set<{ dismiss: (reason?: unknown) => void; forceUnmount: () => void }>();

  public static dismissAll(reason?: unknown): void {
    Array.from(this.activeModals).forEach(({ dismiss, forceUnmount }) => {
      try {
        dismiss(reason);
      } catch (error) {
        diagnosticLogger.error('Failed to dismiss React modal', error);
      }
      forceUnmount();
    });
  }

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
      let settled = false;

      function unmount() {
        if (!mountNode) {
          return;
        }

        const node = mountNode;
        mountNode = null;
        ReactModal.activeModals.delete(activeModal);
        try {
          ReactDOM.unmountComponentAtNode(node);
        } finally {
          node.remove();
        }
      }

      function settlePendingResult() {
        if (pendingResult) {
          const { handler, value } = pendingResult;
          pendingResult = null;
          handler(value);
        }
      }

      function onExited() {
        settlePendingResult();
        try {
          unmount();
        } catch (error) {
          diagnosticLogger.error('Failed to unmount React modal after exit animation', error);
        }
      }

      function forceUnmount() {
        settlePendingResult();
        try {
          unmount();
        } catch (error) {
          diagnosticLogger.error('Failed to force-unmount React modal', error);
        }
      }

      const destroy = (resultHandler: (result: any) => void) => (result?: any) => {
        if (!mountNode || settled) {
          return;
        }

        if (pendingResult) {
          return;
        }

        pendingResult = { handler: resultHandler, value: result };
        settled = true;
        // Use react-bootstrap modal lifecycle, i.e. show=false, which triggers onExited
        show = false;
        try {
          render();
        } catch (error) {
          settlePendingResult();
          try {
            unmount();
          } catch (unmountError) {
            diagnosticLogger.error('Failed to unmount React modal after exit render failure', unmountError);
          }
          diagnosticLogger.error('Failed to render React modal exit', error);
        }
      };

      const handleClose = destroy(resolve);
      const handleDismiss = destroy(reject);
      const activeModal = { dismiss: handleDismiss, forceUnmount };

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

      ReactModal.activeModals.add(activeModal);
      try {
        render();
      } catch (error) {
        settled = true;
        ReactModal.activeModals.delete(activeModal);
        try {
          unmount();
        } catch (unmountError) {
          diagnosticLogger.error('Failed to unmount React modal after initial render failure', unmountError);
        }
        throw error;
      }
    });

    modalPromise.catch(() => {});

    return modalPromise;
  }
}
