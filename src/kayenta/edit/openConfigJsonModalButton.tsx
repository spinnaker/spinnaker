import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';

import * as Creators from '../actions/creators';
import ConfigJsonModal from './configJsonModal';

interface IOpenConfigJsonModalDispatchProps {
  openConfigJsonModal: () => void;
}

/*
 * Button for opening the edit config JSON modal.
 */
function OpenConfigJsonModalButton({ openConfigJsonModal }: IOpenConfigJsonModalDispatchProps) {
  return (
    <div>
      <button className="passive" onClick={openConfigJsonModal}>
        <i className="far fa-file-code"/>
        <span>JSON</span>
      </button>
      <ConfigJsonModal/>
    </div>
  );
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IOpenConfigJsonModalDispatchProps {
  return {
    openConfigJsonModal: () => dispatch(Creators.openConfigJsonModal()),
  };
}

export default connect(null, mapDispatchToProps)(OpenConfigJsonModalButton);
