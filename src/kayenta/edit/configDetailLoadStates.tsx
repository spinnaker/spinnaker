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
        <CenteredDetail>
          <NgReact.Spinner radius={20} width={3} length={20}/>
        </CenteredDetail>
      );

    case ConfigDetailLoadState.Loaded:
      return <ConfigDetail/>;

    case ConfigDetailLoadState.Error:
      return (
        <CenteredDetail>
          <p>Could not load canary config.</p>
        </CenteredDetail>
      );

    default:
      return null;
  }
}

function mapStateToProps(state: ICanaryState): IConfigLoadStatesProps {
  return {
    configLoadState: state.configLoadState,
  };
}


export default connect(mapStateToProps)(ConfigDetailLoadStates);
