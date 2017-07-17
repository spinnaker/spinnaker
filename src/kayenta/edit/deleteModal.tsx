import * as React from 'react';
import { connect } from 'react-redux';
import { Button, Modal } from 'react-bootstrap';
import * as classNames from 'classnames';

import { SubmitButton } from '@spinnaker/core';

import {
  DELETE_CONFIG_MODAL_CLOSE,
  DELETE_CONFIG_DELETING
} from '../actions/index';
import { ICanaryState } from '../reducers/index';

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
        <Button onClick={closeDeleteConfigModal}>Cancel</Button>
        <SubmitButton
          label="Delete"
          submitting={deleteConfigState === DeleteConfigState.Deleting}
          onClick={deleteConfig}
        />
      </Modal.Footer>
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
      dispatch({type: DELETE_CONFIG_DELETING})
    },
  };
}

function mapStateToProps(state: ICanaryState): IDeleteModalStateProps {
  return {
    show: state.deleteConfigModalOpen,
    selectedConfigName: state.selectedConfig ? state.selectedConfig.name : null,
    deleteConfigState: state.deleteConfigState,
    deleteConfigErrorMessage: state.deleteConfigErrorMessage,
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(DeleteConfigModal);
