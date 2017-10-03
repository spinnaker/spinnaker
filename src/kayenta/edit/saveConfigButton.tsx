import * as React from 'react';
import { connect } from 'react-redux';

import { SubmitButton } from '@spinnaker/core';

import { ICanaryState } from '../reducers';
import * as Creators from '../actions/creators';
import { SaveConfigState } from './save';

interface ISaveButtonStateProps {
  saveConfigState: SaveConfigState;
  inSyncWithServer: boolean;
}

interface ISaveButtonDispatchProps {
  saveConfig: () => void;
}

/*
 * Button for saving a canary config.
 */
function SaveConfigButton({ saveConfigState, inSyncWithServer, saveConfig }: ISaveButtonStateProps & ISaveButtonDispatchProps) {
  if (inSyncWithServer) {
    return (
      <span className="btn btn-link disabled">
        <i className="fa fa-check-circle-o"/> In sync with server
      </span>
    );
  } else {
    return (
      <SubmitButton
        label="Save Changes"
        onClick={saveConfig}
        submitting={saveConfigState === SaveConfigState.Saving}
      />
    );
  }
}

function mapStateToProps(state: ICanaryState): ISaveButtonStateProps {
  return {
    saveConfigState: state.selectedConfig.save.state,
    inSyncWithServer: state.selectedConfig.isInSyncWithServer,
  };
}

function mapDispatchToProps(dispatch: any): ISaveButtonDispatchProps {
  return {
    saveConfig: () => {
      dispatch(Creators.saveConfig());
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(SaveConfigButton);
