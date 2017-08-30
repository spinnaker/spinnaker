import * as React from 'react';
import { connect } from 'react-redux';
import * as classNames from 'classnames';

import { ICanaryState } from '../reducers';
import { DISMISS_SAVE_CONFIG_ERROR } from '../actions/index';
import { SaveConfigState } from './save';

interface ISaveErrorStateProps {
  saveConfigState: SaveConfigState;
  saveConfigErrorMessage: string;
}

interface ISaveErrorDispatchProps {
  dismissError: () => void;
}

/*
 * Renders canary config save error.
 */
function SaveConfigError({ saveConfigState, saveConfigErrorMessage, dismissError }: ISaveErrorStateProps & ISaveErrorDispatchProps) {
  return saveConfigState === SaveConfigState.Error && (
    <span className={classNames('alert', 'alert-danger')}>
      {buildErrorMessage(saveConfigErrorMessage)}
      <a
        className={classNames('alert-dismiss', 'clickable')}
        onClick={dismissError}
      > [dismiss]
      </a>
    </span>
  );
}

function buildErrorMessage(saveConfigErrorMessage: string): string {
  const message = 'The was an error saving your config';
  return saveConfigErrorMessage
    ? message + `: ${saveConfigErrorMessage}.`
    : message + '.';
}

function mapStateToProps(state: ICanaryState): ISaveErrorStateProps {
  return {
    saveConfigState: state.selectedConfig.save.state,
    saveConfigErrorMessage: state.selectedConfig.save.error,
  };
}

function mapDispatchToProps(dispatch: any): ISaveErrorDispatchProps {
  return {
    dismissError: () => {
      dispatch({
        type: DISMISS_SAVE_CONFIG_ERROR,
      });
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(SaveConfigError);
