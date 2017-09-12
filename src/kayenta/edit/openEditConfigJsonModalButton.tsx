import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';

import * as Creators from '../actions/creators';
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
      <button className="passive" onClick={openEditConfigJsonModal}>JSON</button>
      <EditConfigJsonModal/>
    </div>
  );
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IOpenEditConfigJsonModalDispatchProps {
  return {
    openEditConfigJsonModal: () => dispatch(Creators.openEditConfigJsonModal()),
  };
}

export default connect(null, mapDispatchToProps)(OpenEditConfigJsonModalButton);
