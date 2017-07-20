import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';

import { EDIT_CONFIG_JSON_MODAL_OPEN } from '../actions/index';
import EditConfigJsonModal from './editConfigJsonModal';

interface IOpenEditConfigJsonModalDispatchProps {
  openEditConfigJsonModal: () => void;
}

/*
 * Button for opening the edit config JSON modal.
 */
function OpenEditConfigJsonModalButton({ openEditConfigJsonModal }: IOpenEditConfigJsonModalDispatchProps) {
  return (
    <div>
      <button onClick={openEditConfigJsonModal}>JSON</button>
      <EditConfigJsonModal/>
    </div>
  );
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IOpenEditConfigJsonModalDispatchProps {
  return {
    openEditConfigJsonModal: () => {
      dispatch({type: EDIT_CONFIG_JSON_MODAL_OPEN});
    }
  };
}

export default connect(null, mapDispatchToProps)(OpenEditConfigJsonModalButton);
