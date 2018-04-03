import * as React from 'react';
import { connect } from 'react-redux';

import * as Creators from 'kayenta/actions/creators';
import DeleteConfigModal from './deleteModal';
import { ICanaryState } from 'kayenta/reducers';
import { DisableableButton, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';

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
      <DisableableButton
        className="passive"
        disabled={disabled}
        onClick={openDeleteConfigModal}
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      >
        <i className="fa fa-trash"/>
        <span>Delete</span>
      </DisableableButton>
      <DeleteConfigModal/>
    </div>
  );
}

function mapStateToProps(state: ICanaryState) {
  return {
    disabled: state.selectedConfig.config && state.selectedConfig.config.isNew,
  };
}

function mapDispatchToProps(dispatch: any): IDeleteButtonDispatchProps {
  return {
    openDeleteConfigModal: () => dispatch(Creators.openDeleteConfigModal()),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(DeleteConfigButton);
