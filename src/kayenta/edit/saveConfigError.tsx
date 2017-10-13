import * as React from 'react';
import { connect } from 'react-redux';
import * as classNames from 'classnames';

import { ICanaryState } from '../reducers';
import * as Creators from '../actions/creators';
import { AsyncRequestState } from '../reducers/asyncRequest';

interface ISaveErrorStateProps {
  saveConfigState: AsyncRequestState;
  saveConfigErrorMessage: string;
}

interface ISaveErrorDispatchProps {
  dismissError: () => void;
}

/*
 * Renders canary config save error.
 */
function SaveConfigError({ saveConfigState, saveConfigErrorMessage, dismissError }: ISaveErrorStateProps & ISaveErrorDispatchProps) {
  return saveConfigState === AsyncRequestState.Failed && (
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
      dispatch(Creators.dismissSaveConfigError());
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(SaveConfigError);
