import * as React from 'react';
import { connect } from 'react-redux';

import { SubmitButton } from '@spinnaker/core';

import { ICanaryState } from '../reducers';
import * as Creators from '../actions/creators';
import { AsyncRequestState } from '../reducers/asyncRequest';

interface ISaveButtonStateProps {
  saveConfigState: AsyncRequestState;
  inSyncWithServer: boolean;
  disable: boolean;
}

interface ISaveButtonDispatchProps {
  saveConfig: () => void;
}

/*
 * Button for saving a canary config.
 */
function SaveConfigButton({ saveConfigState, inSyncWithServer, saveConfig, disable }: ISaveButtonStateProps & ISaveButtonDispatchProps) {
  if (inSyncWithServer && saveConfigState !== AsyncRequestState.Requesting) {
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
        isDisabled={disable}
        submitting={saveConfigState === AsyncRequestState.Requesting}
      />
    );
  }
}

function mapStateToProps(state: ICanaryState): ISaveButtonStateProps {
  const appName = state.data.application.name;
  const configIsEditableInThisApp = state.selectedConfig.config
    && state.selectedConfig.config.applications.includes(appName);
  // TODO(dpeach): show tooltip to indicate why the config can't be edited in this application.
  const disable = !!state.selectedConfig.validationErrors.length || !configIsEditableInThisApp;
  return {
    saveConfigState: state.selectedConfig.save.state,
    inSyncWithServer: state.selectedConfig.isInSyncWithServer,
    disable,
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
