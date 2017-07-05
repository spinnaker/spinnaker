import * as React from 'react';
import { connect } from 'react-redux';

import { NgReact } from '@spinnaker/core';

import { ICanaryState } from '../reducers/index';
import { ConfigDetailLoadState } from './configDetailLoader';
import MetricList from './metricList';

interface IConfigLoadOutcomesProps {
  configLoadState: ConfigDetailLoadState;
}

/*
 * Renders appropriate view given the configuration detail's load state.
 */
function ConfigDetailLoadStates({ configLoadState }: IConfigLoadOutcomesProps) {
  switch (configLoadState) {
    case ConfigDetailLoadState.Loading:
      return (
        <section className="text-center">
          <NgReact.Spinner radius={20} width={3} length={20}/>
        </section>
      );

    case ConfigDetailLoadState.Loaded:
      return <MetricList/>;

    case ConfigDetailLoadState.Error:
      return (
        <section className="text-center">
          <p>Could not load canary config.</p>
        </section>
      );

    default:
      return null;
  }
}

function mapStateToProps(state: ICanaryState): IConfigLoadOutcomesProps {
  return {
    configLoadState: state.configLoadState,
  };
}


export default connect(mapStateToProps)(ConfigDetailLoadStates);
