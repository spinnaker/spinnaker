import * as React from 'react';
import { connect } from 'react-redux';
import { isEqual, omit } from 'lodash';

import { SubmitButton } from '@spinnaker/core';

import { ICanaryState } from '../reducers';
import * as Creators from '../actions/creators';
import { SaveConfigState } from './save';
import { mapStateToConfig } from '../service/canaryConfig.service';

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

function isInSyncWithServer(state: ICanaryState): boolean {
  const editedConfig = mapStateToConfig(state);
  if (!editedConfig) {
    return true;
  } else {
    // The UI adds these ids and Kayenta does not persist them.
    editedConfig.metrics = editedConfig.metrics.map(metric => omit(metric, 'id'));

    const originalConfig = state.data.configs.find(c => c.name === editedConfig.name);
    return isEqual(editedConfig, originalConfig);
  }
}

function mapStateToProps(state: ICanaryState): ISaveButtonStateProps {
  return {
    saveConfigState: state.selectedConfig.save.state,
    inSyncWithServer: isInSyncWithServer(state),
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
