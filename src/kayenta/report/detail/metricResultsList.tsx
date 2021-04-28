import classNames from 'classnames';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryAnalysisResult } from 'kayenta/domain/ICanaryJudgeResult';
import { Table } from 'kayenta/layout/table';
import { ICanaryState } from 'kayenta/reducers';
import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { MetricClassificationLabel } from '../../domain';
import MetricFilters from './metricResultsClassificationFilters';
import { metricResultsColumns } from './metricResultsColumns';
import MultipleResultsTable from './multipleResultsTable';

import './metricResultsList.less';

export interface IResultsListOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IResultsListDispatchProps {
  select: (metric: string) => void;
}

interface IResultsListStateProps {
  selectedMetric: string;
  metricFilters: MetricClassificationLabel[];
}

export interface IMetricResultsTableRow {
  metricName: string;
  results: ICanaryAnalysisResult[];
  hasMultipleRows: boolean;
}

const buildTableRows = (
  results: ICanaryAnalysisResult[],
  metricFilters: MetricClassificationLabel[],
): IMetricResultsTableRow[] => {
  const unfilteredCounts = results.reduce((map, result) => {
    if (!map.has(result.name)) {
      map.set(result.name, 0);
    }
    map.set(result.name, map.get(result.name) + 1);
    return map;
  }, new Map<string, number>());

  const tableRowsByMetricName = results
    .filter((result) => !metricFilters.length || metricFilters.includes(result.classification))
    .reduce(
      (map, result) =>
        map.has(result.name)
          ? map.set(result.name, {
              metricName: result.name,
              results: map.get(result.name).results.concat(result),
              hasMultipleRows: unfilteredCounts.get(result.name) > 1,
            })
          : map.set(result.name, {
              metricName: result.name,
              results: [result],
              hasMultipleRows: unfilteredCounts.get(result.name) > 1,
            }),
      new Map<string, IMetricResultsTableRow>(),
    );

  return Array.from(tableRowsByMetricName.values());
};

const buildRowForMetricWithMultipleResults = (row: IMetricResultsTableRow) => {
  if (!row.hasMultipleRows) {
    return null;
  }

  return (
    <li className="horizontal multiple-results">
      <section className="vertical flex-fill">
        <div>{row.metricName}</div>
        <MultipleResultsTable results={row.results} />
      </section>
    </li>
  );
};

const ResultsList = ({
  results,
  select,
  selectedMetric,
  metricFilters,
}: IResultsListOwnProps & IResultsListDispatchProps & IResultsListStateProps) => {
  const rows = buildTableRows(results, metricFilters);
  return (
    <section className="vertical metric-results-list flex-1">
      <MetricFilters results={results} />
      <Table
        rowKey={(r) => r.metricName}
        tableBodyClassName="list-unstyled tabs-vertical flex-1 sp-padding-xl-bottom"
        rowClassName={(r) => classNames('horizontal', { selected: r.results[0].id === selectedMetric })}
        rows={rows}
        columns={metricResultsColumns}
        onRowClick={(r) => select(r.results[0].id)}
        className="flex-1 vertical"
        customRow={buildRowForMetricWithMultipleResults}
      />
    </section>
  );
};

const mapStateToProps = (state: ICanaryState): IResultsListStateProps => ({
  selectedMetric: state.selectedRun.selectedMetric,
  metricFilters: state.selectedRun.metricFilters,
});

const mapDispatchToProps = (
  dispatch: Dispatch<ICanaryState>,
  ownProps: IResultsListOwnProps,
): IResultsListOwnProps & IResultsListDispatchProps => ({
  select: (metricId: string) => dispatch(Creators.selectReportMetric({ metricId })),
  ...ownProps,
});

export default connect(mapStateToProps, mapDispatchToProps)(ResultsList);
