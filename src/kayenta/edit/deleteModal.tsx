import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';
import * as classNames from 'classnames';

import * as Creators from 'kayenta/actions/creators';
import { ICanaryState } from '../reducers/index';
import Styleguide from '../layout/styleguide';
import { AsyncRequestState } from '../reducers/asyncRequest';

interface IDeleteModalDispatchProps {
  closeDeleteConfigModal: () => void;
  deleteConfig: () => void;
}

interface IDeleteModalStateProps {
  show: boolean;
  selectedConfigName: string;
  deleteConfigState: AsyncRequestState;
  deleteConfigErrorMessage: string;
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
        {deleteConfigState === AsyncRequestState.Failed && (
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

function mapDispatchToProps(dispatch: (action: Action & any) => void): IDeleteModalDispatchProps {
  return {
    closeDeleteConfigModal: () => dispatch(Creators.closeDeleteConfigModal()),
    deleteConfig: () => dispatch(Creators.deleteConfig()),
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
