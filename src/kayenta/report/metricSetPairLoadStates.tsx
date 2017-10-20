import * as React from 'react';
import { connect } from 'react-redux';

import LoadStatesBuilder from 'kayenta/components/loadStates';
import { ICanaryState } from '../reducers/index';
import { AsyncRequestState } from '../reducers/asyncRequest';
import MetricSetPair from './metricSetPair';

const MetricSetPairLoadStates = ({ state }: { state: AsyncRequestState }) => {
  const LoadStates = new LoadStatesBuilder()
    .onFulfilled(<MetricSetPair/>)
    .onFailed(
      <h3 className="heading-3">Could not load metrics.</h3>
    ).build();

  return <LoadStates state={state}/>;
};

const mapStateToProps = (state: ICanaryState) => ({
  state: state.selectedRun.metricSetPair.load,
});

export default connect(mapStateToProps)(MetricSetPairLoadStates);
