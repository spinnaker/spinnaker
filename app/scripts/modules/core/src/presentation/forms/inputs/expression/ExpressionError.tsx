import React from 'react';
import { Modal } from 'react-bootstrap';

import { ISpelError } from './evaluateExpression';
import { ValidationMessage } from '../../validation';

export interface IExpressionErrorProps {
  spelError: ISpelError;
}

export interface IExpressionErrorState {
  showContextModal: boolean;
}

export class ExpressionError extends React.Component<IExpressionErrorProps, IExpressionErrorState> {
  public state: IExpressionErrorState = { showContextModal: false };

  public render() {
    const { spelError } = this.props;
    if (!spelError || !spelError.message) {
      return null;
    }

    const toggleModal = () => this.setState((state) => ({ showContextModal: !state.showContextModal }));

    return (
      <div>
        <Modal show={this.state.showContextModal} onHide={toggleModal}>
          <Modal.Header>
            <Modal.Title>{spelError.message}</Modal.Title>
          </Modal.Header>

          <Modal.Body>
            <pre>{spelError.context}</pre>
          </Modal.Body>

          <Modal.Footer>
            <button className="primary passive" type="button" onClick={toggleModal}>
              Close
            </button>
          </Modal.Footer>
        </Modal>

        <ValidationMessage type="error" message={spelError.message} />

        <button className="link" type="button" onClick={toggleModal}>
          Show expression context where the error occurred
        </button>
      </div>
    );
  }
}
