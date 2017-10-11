import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import MetricResultRow from './metricResultRow';
import { ICanaryState } from '../reducers/index';
import * as Creators from 'kayenta/actions/creators';
import { metricResultsColumns } from './metricResultsColumns';
import MetricResultsListHeader from './metricResultsListHeader';

interface IResultsListOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IResultsListDispatchProps {
  select: (metric: string) => void;
}

const ResultsList = ({ results, select }: IResultsListOwnProps & IResultsListDispatchProps) => (
  <section className="vertical">
    <MetricResultsListHeader columns={metricResultsColumns}/>
    <ul className="list-unstyled">
      {results.map(r => (
        <li key={r.name}>
          <MetricResultRow columns={metricResultsColumns} onClick={select} result={r}/>
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
    dispatch(Creators.selectResultMetric({ metric })),
  ...ownProps,
});

export default connect(null, mapDispatchToProps)(ResultsList);
