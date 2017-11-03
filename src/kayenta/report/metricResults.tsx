import * as React from 'react';
import { connect } from 'react-redux';
import { sortBy } from 'lodash';

import ListDetail from '../layout/listDetail';
import { ICanaryState } from '../reducers/index';
import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import MetricResultsList from './metricResultsList';
import MetricResultDetail from './metricResultDetail';
import { judgeResultSelector } from '../selectors/index';

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
      listWidth={4}
      detail={detail}
      detailWidth={8}
    />
  );
};

const mapStateToProps = (state: ICanaryState): IMetricResultsStateProps => {
  const {
    selectedRun: {
      selectedGroup,
      selectedMetric,
    },
  } = state;
  const result = judgeResultSelector(state);

  // Build list of metric results to render.
  let filter: (r: ICanaryAnalysisResult) => boolean;
  if (!selectedGroup) {
    filter = () => true;
  } else {
    filter = r => r.groups.includes(selectedGroup);
  }

  return {
    metricResults: sortBy(Object.values(result.results).filter(filter), 'name'),
    selectedMetricResult: Object.values(result.results).find(r => r.name === selectedMetric),
  };
};

export default connect(mapStateToProps)(MetricResults);
