import * as React from 'react';
import { connect } from 'react-redux';

import { DELETE_CONFIG_MODAL_OPEN } from '../actions/index';
import DeleteConfigModal from './deleteModal';
import { ICanaryState } from '../reducers/index';

interface IDeleteButtonStateProps {
  disabled: boolean;
}

interface IDeleteButtonDispatchProps {
  openDeleteConfigModal: () => void;
}

/*
 * Button for opening the delete canary config confirmation modal.
 */
function DeleteConfigButton({ openDeleteConfigModal, disabled }: IDeleteButtonDispatchProps & IDeleteButtonStateProps) {
  return (
    <div>
      <button className="passive" disabled={disabled} onClick={openDeleteConfigModal}>Delete</button>
      <DeleteConfigModal/>
    </div>
  );
}

function mapStateToProps(state: ICanaryState) {
  return {
    disabled: state.selectedConfig && state.selectedConfig.isNew,
  };
}

function mapDispatchToProps(dispatch: any): IDeleteButtonDispatchProps {
  return {
    openDeleteConfigModal: () => {
      dispatch({type: DELETE_CONFIG_MODAL_OPEN});
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(DeleteConfigButton);
