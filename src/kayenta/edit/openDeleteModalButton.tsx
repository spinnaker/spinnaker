import * as React from 'react';
import { connect } from 'react-redux';

import { DELETE_CONFIG_MODAL_OPEN } from '../actions/index';
import DeleteConfigModal from './deleteModal';

interface IDeleteButtonDispatchProps {
  openDeleteConfigModal: () => void;
}

/*
 * Button for opening the delete canary config confirmation modal.
 */
function DeleteConfigButton({ openDeleteConfigModal }: IDeleteButtonDispatchProps) {
  return (
    <div>
      <button onClick={openDeleteConfigModal}>Delete</button>
      <DeleteConfigModal/>
    </div>
  );
}

function mapDispatchToProps(dispatch: any): IDeleteButtonDispatchProps {
  return {
    openDeleteConfigModal: () => {
      dispatch({type: DELETE_CONFIG_MODAL_OPEN});
    }
  };
}

export default connect(null, mapDispatchToProps)(DeleteConfigButton);
