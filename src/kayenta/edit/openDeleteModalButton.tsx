import * as React from 'react';
import { connect } from 'react-redux';
import { Button } from 'react-bootstrap';

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
      <Button onClick={openDeleteConfigModal}>Delete</Button>
      {/* Is this how people place modals in the component tree? */}
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
