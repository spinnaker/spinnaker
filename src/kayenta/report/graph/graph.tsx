import * as React from 'react';
import { connect } from 'react-redux';

import { GraphType, metricSetPairGraphService } from './metricSetPairGraph.service';
import { ICanaryState } from 'kayenta/reducers';
import { IMetricSetPair } from 'kayenta/domain/IMetricSetPair';
import { ICanaryAnalysisResult } from 'kayenta/domain/ICanaryJudgeResult';
import { metricResultsSelector } from 'kayenta/selectors';
import { CanarySettings } from 'kayenta/canary.settings';

// TODO: externalize this as app state.
const graphType = GraphType.AmplitudeVsTime;

interface IMetricSetPairGraphStateProps {
  pair: IMetricSetPair;
  result: ICanaryAnalysisResult;
}

const MetricSetPairGraph = ({ pair, result }: IMetricSetPairGraphStateProps) => {
  const delegate = metricSetPairGraphService.getDelegate(CanarySettings.graphImplementation);
  if (!delegate || !delegate.handlesGraphType(graphType)) {
    return <h3 className="heading-3">Could not load graph.</h3>;
  }

  const Graph = delegate.getGraph();
  return <Graph metricSetPair={pair} result={result} type={graphType}/>
};

const mapStateToProps  = (state: ICanaryState): IMetricSetPairGraphStateProps => {
  const selectedMetric = state.selectedRun.selectedMetric;
  return {
    pair: state.selectedRun.metricSetPair.pair,
    result: metricResultsSelector(state)[selectedMetric],
  };
};

export default connect(mapStateToProps)(MetricSetPairGraph);
