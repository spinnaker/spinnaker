import * as React from 'react';
import { connect } from 'react-redux';

import ListDetail from '../layout/listDetail';
import { ICanaryState } from '../reducers/index';
import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import ResultsList from './resultsList';
import ResultDetail from './resultDetail';

interface IResultsStateProps {
  results: ICanaryAnalysisResult[];
  selectedMetricResult: ICanaryAnalysisResult;
}

const Results = ({ results, selectedMetricResult }: IResultsStateProps) => {
  const list = <ResultsList results={results}/>;
  const detail = <ResultDetail result={selectedMetricResult}/>;

  return (
    <ListDetail
      list={list}
      listWidth={1}
      detail={detail}
      detailWidth={1}
    />
  );
};

const mapStateToProps = (state: ICanaryState): IResultsStateProps => {
  const {
    selectedReport: {
      report,
      selectedGroup,
      selectedMetric,
    },
  } = state;

  // Build list of metric results to render.
  let filter: (result: ICanaryAnalysisResult) => boolean;
  if (!selectedGroup) {
    filter = () => true;
  } else {
    filter = result => result.groups.includes(selectedGroup);
  }

  return {
    results: Object.values(report.results).filter(filter),
    selectedMetricResult: Object.values(report.results).find(r => r.name === selectedMetric),
  };
};

export default connect(mapStateToProps)(Results);
