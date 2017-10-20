import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import MetricResultRow from './metricResultRow';
import { ICanaryState } from '../reducers/index';
import * as Creators from 'kayenta/actions/creators';
import { metricResultsColumns } from './metricResultsColumns';
import MetricResultsListHeader from './metricResultsListHeader';

export interface IResultsListOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IResultsListDispatchProps {
  select: (metric: string) => void;
}

interface IResultsListStateProps {
  selectedMetric: string;
}

const ResultsList = ({ results, select, selectedMetric }: IResultsListOwnProps & IResultsListDispatchProps & IResultsListStateProps) => (
  <section className="vertical">
    <ul className="list-unstyled tabs-vertical">
      <MetricResultsListHeader columns={metricResultsColumns}/>
      {results.map(r => (
        <li
          key={r.name}
          onClick={() => select(r.name)}
          className={r.name === selectedMetric ? 'selected' : ''}
        >
          <MetricResultRow columns={metricResultsColumns} result={r}/>
        </li>
      ))}
    </ul>
  </section>
);

const mapStateToProps = (state: ICanaryState): IResultsListStateProps => ({
  selectedMetric: state.selectedRun.selectedMetric,
});

const mapDispatchToProps = (
  dispatch: Dispatch<ICanaryState>,
  ownProps: IResultsListOwnProps,
): IResultsListOwnProps & IResultsListDispatchProps => ({
  select: (metric: string) =>
    dispatch(Creators.selectReportMetric({ metric })),
  ...ownProps,
});

export default connect(mapStateToProps, mapDispatchToProps)(ResultsList);
