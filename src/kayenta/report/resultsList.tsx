import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import ResultRow from './resultRow';
import { ICanaryState } from '../reducers/index';
import * as Creators from 'kayenta/actions/creators';

interface IResultsListOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IResultsListDispatchProps {
  select: (event: any) => void;
}

const ResultsList = ({ results, select }: IResultsListOwnProps & IResultsListDispatchProps) => (
  <ul className="list-unstyled">
    {results.map(r => (
      <li key={r.name}>
        <ResultRow onClick={select} result={r}/>
      </li>
    ))}
  </ul>
);

const mapDispatchToProps = (
  dispatch: Dispatch<ICanaryState>,
  ownProps: IResultsListOwnProps,
): IResultsListOwnProps & IResultsListDispatchProps => ({
  select: (event: any) =>
    dispatch(Creators.selectReportMetricResult({ metric: event.target.dataset.metric })),
  ...ownProps,
});

export default connect(null, mapDispatchToProps)(ResultsList);
