import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import ConfigDetail from './configDetail';
import CenteredDetail from '../layout/centeredDetail';
import { AsyncRequestState } from '../reducers/asyncRequest';
import LoadStatesBuilder from 'kayenta/components/loadStates';

interface IConfigLoadStatesProps {
  configLoadState: AsyncRequestState;
}

/*
 * Renders appropriate view given the configuration detail's load state.
 */
function ConfigDetailLoadStates({ configLoadState }: IConfigLoadStatesProps) {
  const LoadStates = new LoadStatesBuilder()
    .onFulfilled(<ConfigDetail/>)
    .onFailed(
      <CenteredDetail>
        <h3 className="heading-3">Could not load canary config.</h3>
      </CenteredDetail>
    ).build();

  return <LoadStates state={configLoadState}/>;
}

function mapStateToProps(state: ICanaryState): IConfigLoadStatesProps {
  return {
    configLoadState: state.selectedConfig.load.state,
  };
}


export default connect(mapStateToProps)(ConfigDetailLoadStates);
