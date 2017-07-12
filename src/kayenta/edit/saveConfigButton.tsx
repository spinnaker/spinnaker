import * as React from 'react';
import { connect } from 'react-redux';

import { SubmitButton } from '@spinnaker/core';

import { ICanaryState } from '../reducers';
import { SAVE_CONFIG_SAVING } from '../actions/index';
import { SaveConfigState } from './save';

interface ISaveButtonStateProps {
  saveConfigState: SaveConfigState;
}

interface ISaveButtonDispatchProps {
  saveConfig: () => void;
}

/*
 * Button for saving a canary config.
 */
function SaveConfigButton({ saveConfigState, saveConfig }: ISaveButtonStateProps & ISaveButtonDispatchProps) {
  return (
    <SubmitButton
      label="Save Changes"
      onClick={saveConfig}
      submitting={saveConfigState === SaveConfigState.Saving}
    />
  );
}

function mapStateToProps(state: ICanaryState): ISaveButtonStateProps {
  return {
    saveConfigState: state.saveConfigState,
  };
}

function mapDispatchToProps(dispatch: any): ISaveButtonDispatchProps {
  return {
    saveConfig: () => {
      dispatch({ type: SAVE_CONFIG_SAVING });
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(SaveConfigButton);
