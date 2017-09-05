import * as React from 'react';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';
import * as classNames from 'classnames';

import {
  DELETE_CONFIG_MODAL_CLOSE,
  DELETE_CONFIG_REQUEST
} from '../actions/index';
import { ICanaryState } from '../reducers/index';
import Styleguide from '../layout/styleguide';

interface IDeleteModalDispatchProps {
  closeDeleteConfigModal: () => void;
  deleteConfig: () => void;
}

interface IDeleteModalStateProps {
  show: boolean;
  selectedConfigName: string;
  deleteConfigState: DeleteConfigState;
  deleteConfigErrorMessage: string;
}

export enum DeleteConfigState {
  Deleting,
  Completed,
  Error,
}

/*
 * Modal to confirm canary config deletion.
 */
function DeleteConfigModal({ show, selectedConfigName, deleteConfigState, deleteConfigErrorMessage, closeDeleteConfigModal, deleteConfig }: IDeleteModalDispatchProps & IDeleteModalStateProps) {
  return (
    <Modal show={show} onHide={null}>
      <Styleguide>
        <Modal.Header>
          <Modal.Title>Really delete {selectedConfigName}?</Modal.Title>
        </Modal.Header>
        { deleteConfigState === DeleteConfigState.Error && (
          <Modal.Body>
            {/*TODO: create generic error message component */}
            <span className={classNames('alert', 'alert-danger')}>
              {buildErrorMessage(deleteConfigErrorMessage)}
            </span>
          </Modal.Body>
        )}
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li><button className="passive" onClick={closeDeleteConfigModal}>Cancel</button></li>
            <li><button className="primary" onClick={deleteConfig}>Delete</button></li>
          </ul>
        </Modal.Footer>
      </Styleguide>
    </Modal>
  );
}

function buildErrorMessage(deleteConfigErrorMessage: string): string {
  const message = 'The was an error deleting your config';
  return deleteConfigErrorMessage
    ? message + `: ${deleteConfigErrorMessage}.`
    : message + '.';
}

function mapDispatchToProps(dispatch: any): IDeleteModalDispatchProps {
  return {
    closeDeleteConfigModal: () => {
      dispatch({type: DELETE_CONFIG_MODAL_CLOSE});
    },
    deleteConfig: () => {
      dispatch({type: DELETE_CONFIG_REQUEST})
    },
  };
}

function mapStateToProps(state: ICanaryState): IDeleteModalStateProps {
  return {
    show: state.app.deleteConfigModalOpen,
    selectedConfigName: state.selectedConfig.config ? state.selectedConfig.config.name : null,
    deleteConfigState: state.selectedConfig.destroy.state,
    deleteConfigErrorMessage: state.selectedConfig.destroy.error,
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(DeleteConfigModal);
