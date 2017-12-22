import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import { ICanaryState } from '../reducers/index';
import * as Creators from 'kayenta/actions/creators';
import { Table } from 'kayenta/layout/table';
import { metricResultsColumns } from './metricResultsColumns';

import './metricResultsList.less';

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
  <section className="vertical metric-results-list">
    <Table
      rowKey={r => r.name}
      tableBodyClassName="list-unstyled tabs-vertical"
      rowClassName={r => 'horizontal ' + (r.name === selectedMetric ? 'selected' : '')}
      rows={results}
      columns={metricResultsColumns}
      onRowClick={r => select(r.name)}
    />
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
