import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import ResultRow from './resultRow';
import { ICanaryState } from '../reducers/index';
import * as Creators from 'kayenta/actions/creators';
import { resultsListColumns } from './resultsListColumns';
import ResultsListHeader from './resultsListHeader';

interface IResultsListOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IResultsListDispatchProps {
  select: (metric: string) => void;
}

const ResultsList = ({ results, select }: IResultsListOwnProps & IResultsListDispatchProps) => (
  <section className="vertical">
    <ResultsListHeader columns={resultsListColumns}/>
    <ul className="list-unstyled">
      {results.map(r => (
        <li key={r.name}>
          <ResultRow columns={resultsListColumns} onClick={select} result={r}/>
        </li>
      ))}
    </ul>
  </section>
);

const mapDispatchToProps = (
  dispatch: Dispatch<ICanaryState>,
  ownProps: IResultsListOwnProps,
): IResultsListOwnProps & IResultsListDispatchProps => ({
  select: (metric: string) =>
    dispatch(Creators.selectReportMetricResult({ metric })),
  ...ownProps,
});

export default connect(null, mapDispatchToProps)(ResultsList);
