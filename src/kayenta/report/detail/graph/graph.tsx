import * as React from 'react';
import { connect } from 'react-redux';

import { GraphType, metricSetPairGraphService } from './metricSetPairGraph.service';
import { ICanaryState } from 'kayenta/reducers';
import { IMetricSetPair } from 'kayenta/domain/IMetricSetPair';
import { ICanaryAnalysisResult } from 'kayenta/domain/ICanaryJudgeResult';
import { metricResultsSelector } from 'kayenta/selectors';

interface IMetricSetPairGraphStateProps {
  pair: IMetricSetPair;
  result: ICanaryAnalysisResult;
  graphType: GraphType;
}

const GRAPH_IMPLEMENTATIONS = ['chartjs', 'plotly'];

const MetricSetPairGraph = ({ pair, result, graphType }: IMetricSetPairGraphStateProps) => {
  const delegates = GRAPH_IMPLEMENTATIONS
    .map(name => metricSetPairGraphService.getDelegate(name))
    .filter(d => !!d);

  const delegate = delegates.find(candidate => candidate.handlesGraphType(graphType));
  if (!delegate) {
    return <h3 className="heading-3">Could not load graph.</h3>;
  }

  const Graph = delegate.getGraph();
  return <Graph metricSetPair={pair} result={result} type={graphType}/>;
};

const mapStateToProps = (state: ICanaryState): IMetricSetPairGraphStateProps => {
  const selectedMetric = state.selectedRun.selectedMetric;
  return {
    pair: state.selectedRun.metricSetPair.pair,
    result: metricResultsSelector(state).find(result => result.id === selectedMetric),
    graphType: state.selectedRun.graphType,
  };
};

export default connect(mapStateToProps)(MetricSetPairGraph);
