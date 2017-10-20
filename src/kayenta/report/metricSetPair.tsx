import * as React from 'react';
import { connect } from 'react-redux';

import { IMetricSetPair } from '../domain/IMetricSetPair';
import { ICanaryState } from '../reducers/index';

const MetricSetPair = ({ pair }: { pair: IMetricSetPair }) => (
  <pre>{JSON.stringify(pair, null, 2)}</pre>
);

const mapStateToProps = (state: ICanaryState) => ({
  pair: state.selectedRun.metricSetPair.pair,
});

export default connect(mapStateToProps)(MetricSetPair);
