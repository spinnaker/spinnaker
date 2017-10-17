import * as React from 'react';
import { connect } from 'react-redux';

import ListDetail from '../layout/listDetail';
import { ICanaryState } from '../reducers/index';
import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import MetricResultsList from './metricResultsList';
import MetricResultDetail from './metricResultDetail';

interface IMetricResultsStateProps {
  metricResults: ICanaryAnalysisResult[];
  selectedMetricResult: ICanaryAnalysisResult;
}

const MetricResults = ({ metricResults, selectedMetricResult }: IMetricResultsStateProps) => {
  const list = <MetricResultsList results={metricResults}/>;
  const detail = <MetricResultDetail result={selectedMetricResult}/>;

  return (
    <ListDetail
      list={list}
      listWidth={6}
      detail={detail}
      detailWidth={6}
    />
  );
};

const mapStateToProps = (state: ICanaryState): IMetricResultsStateProps => {
  const {
    selectedResult: {
      result,
      selectedGroup,
      selectedMetric,
    },
  } = state;

  // Build list of metric results to render.
  let filter: (r: ICanaryAnalysisResult) => boolean;
  if (!selectedGroup) {
    filter = () => true;
  } else {
    filter = r => r.groups.includes(selectedGroup);
  }

  return {
    metricResults: Object.values(result.results).filter(filter),
    selectedMetricResult: Object.values(result.results).find(r => r.name === selectedMetric),
  };
};

export default connect(mapStateToProps)(MetricResults);
