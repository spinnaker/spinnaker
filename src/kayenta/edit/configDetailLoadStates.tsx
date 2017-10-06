import * as React from 'react';
import { connect } from 'react-redux';

import { NgReact } from '@spinnaker/core';

import { ICanaryState } from '../reducers/index';
import { ConfigDetailLoadState } from './configDetailLoader';

import ConfigDetail from './configDetail';
import CenteredDetail from '../layout/centeredDetail';

interface IConfigLoadStatesProps {
  configLoadState: ConfigDetailLoadState;
}

/*
 * Renders appropriate view given the configuration detail's load state.
 */
function ConfigDetailLoadStates({ configLoadState }: IConfigLoadStatesProps) {
  switch (configLoadState) {
    case ConfigDetailLoadState.Loading:
      return (
        <div className="spinner">
          <NgReact.Spinner radius={20} width={3} length={20}/>
        </div>
      );

    case ConfigDetailLoadState.Loaded:
      return <ConfigDetail/>;

    case ConfigDetailLoadState.Error:
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
