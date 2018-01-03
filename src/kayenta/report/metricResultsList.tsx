import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import { ICanaryState } from '../reducers/index';
import * as Creators from 'kayenta/actions/creators';
import { Table } from 'kayenta/layout/table';
import { metricResultsColumns } from './metricResultsColumns';

import './metricResultsList.less';
import { ITableProps } from '../layout/table/table';
import { MultipleResultsTable } from './multipleResultsTable';

export interface IResultsListOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IResultsListDispatchProps {
  select: (metric: string) => void;
}

interface IResultsListStateProps {
  selectedMetric: string;
}

export interface IMetricResultsTableRow {
  metricName: string;
  results: ICanaryAnalysisResult[];
}

const buildTableRows = (results: ICanaryAnalysisResult[]): IMetricResultsTableRow[] => {
  const tableRowsByMetricName = results.reduce(
    (map, result) =>
      map.has(result.name)
        ? map.set(result.name, { metricName: result.name, results: map.get(result.name).results.concat(result) })
        : map.set(result.name, { metricName: result.name, results: [result] }),
    new Map<string, IMetricResultsTableRow>()
  );

  return Array.from(tableRowsByMetricName.values());
};

const buildRowForMetricWithMultipleResults = (row: IMetricResultsTableRow, tableProps: ITableProps<IMetricResultsTableRow>) => {
  if (row.results.length < 2) {
    return null;
  }

  return (
    <li
      className={tableProps.rowClassName && tableProps.rowClassName(row)}
      style={{paddingBottom: 0}}
    >
      <section className="vertical flex-fill">
        <div>{row.metricName}</div>
        <MultipleResultsTable results={row.results}/>
      </section>
    </li>
  );
};

const ResultsList = ({ results, select, selectedMetric }: IResultsListOwnProps & IResultsListDispatchProps & IResultsListStateProps) => {
  const rows = buildTableRows(results);
  return (
    <section className="vertical metric-results-list">
      <Table
        rowKey={r => r.metricName}
        tableBodyClassName="list-unstyled tabs-vertical"
        rowClassName={r => 'horizontal ' + (r.metricName === selectedMetric ? 'selected' : '')}
        rows={rows}
        columns={metricResultsColumns}
        onRowClick={r => select(r.metricName)}
        customRow={buildRowForMetricWithMultipleResults}
      />
    </section>
  );
};

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
