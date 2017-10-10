import * as React from 'react';
import { connect } from 'react-redux';

import ListDetail from '../layout/listDetail';
import { ICanaryState } from '../reducers/index';
import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import ResultsList from './resultsList';

interface IResultsStateProps {
  results: ICanaryAnalysisResult[];
}

const Results = ({ results }: IResultsStateProps) => {
  const list = <ResultsList results={results}/>;
  const detail = (
    <div>This is a placeholder for the graph.</div>
  );

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
  // Build list of metric results to render.
  const {
    selectedReport: { report, selectedGroup }
  } = state;

  let filter: (result: ICanaryAnalysisResult) => boolean;
  if (!selectedGroup) {
    filter = () => true;
  } else {
    filter = result => result.groups.includes(selectedGroup);
  }

  return {
    results: Object.values(report.results).filter(filter),
  };
};

export default connect(mapStateToProps)(Results);
