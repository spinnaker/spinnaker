import * as React from 'react';
import { connect } from 'react-redux';

import { NgReact } from '@spinnaker/core';

import { ICanaryState } from '../reducers/index';

import ConfigDetail from './configDetail';
import CenteredDetail from '../layout/centeredDetail';
import { AsyncRequestState } from '../reducers/asyncRequest';

interface IConfigLoadStatesProps {
  configLoadState: AsyncRequestState;
}

/*
 * Renders appropriate view given the configuration detail's load state.
 */
function ConfigDetailLoadStates({ configLoadState }: IConfigLoadStatesProps) {
  switch (configLoadState) {
    case AsyncRequestState.Requesting:
      return (
        <div className="spinner">
          <NgReact.LegacySpinner radius={20} width={3} length={20}/>
        </div>
      );

    case AsyncRequestState.Fulfilled:
      return <ConfigDetail/>;

    case AsyncRequestState.Failed:
      return (
        <CenteredDetail>
          <h3 className="heading-3">Could not load canary config.</h3>
        </CenteredDetail>
      );

    default:
      return null;
  }
}

function mapStateToProps(state: ICanaryState): IConfigLoadStatesProps {
  return {
    configLoadState: state.selectedConfig.load.state,
  };
}


export default connect(mapStateToProps)(ConfigDetailLoadStates);
