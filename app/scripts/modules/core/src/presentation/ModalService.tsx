import { ReactElement } from 'react';
import * as React from 'react';
import * as ReactDOM from 'react-dom';

/** An imperative service for showing a react component as a modal */
export class ReactModal {
  public static show(modal: ReactElement<any>): Promise<any> {
    return new Promise((resolve) => {
      let mountNode = document.createElement('div');
      let show = true;

      render();

      function onExited() {
        if (!mountNode) {
          return;
        }

        ReactDOM.unmountComponentAtNode(mountNode);
        mountNode = null;
      }

      function onHide(action: any) {
        show = false;
        resolve(action);
        render();
      }

      function render() {
        ReactDOM.render(
          React.cloneElement(modal, {
            show,
            onExited,
            onHide,
          }),
          mountNode
        );
      }
    });
  }
}
