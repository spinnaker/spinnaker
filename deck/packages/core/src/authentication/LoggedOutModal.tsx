import React from 'react';
import { Modal } from 'react-bootstrap';

import { ReactModal } from '../presentation/ReactModal';

import './LoggedOutModal.less';

export class LoggedOutModal extends React.Component<{}> {
  public static show(): Promise<void> {
    return ReactModal.show(LoggedOutModal, {});
  }

  public render() {
    return (
      <Modal.Body className="modal-logged-out">
        <svg width="162px" height="143px" viewBox="0 0 162 143">
          <g id="Page-1" stroke="none" strokeWidth="1" fill="none" fillRule="evenodd">
            <g transform="translate(3.000000, 3.000000)">
              <polygon id="Fill-1" fill="#E8AE2F" points="0 138 81 0 156 138" />
              <polygon
                id="Stroke-3"
                stroke="#000000"
                strokeWidth="4"
                strokeLinecap="round"
                strokeLinejoin="round"
                points="0 138 81 0 156 138"
              />
            </g>
          </g>
        </svg>
        <i className="far fa-hourglass" />
        <h3>You have been logged out.</h3>
        <p>Please click below or refresh your browser to log back in.</p>
        <p>
          <button className="btn btn-primary" onClick={() => window.location.reload()}>
            Refresh and log me back in
          </button>
        </p>
      </Modal.Body>
    );
  }
}
