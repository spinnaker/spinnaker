import * as React from 'react';
import { chain } from 'lodash';
import { connect, Dispatch } from 'react-redux';
import * as classNames from 'classnames';

import { ICanaryAnalysisResult } from 'kayenta/domain/ICanaryJudgeResult';
import { Table, ITableColumn } from 'kayenta/layout/table';
import MetricResultClassification from './metricResultClassification';
import { ICanaryState } from 'kayenta/reducers';
import { selectedMetricResultIdSelector } from 'kayenta/selectors';
import * as Creators from 'kayenta/actions/creators';

interface IMultipleResultsTableOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IMultipleResultsTableStateProps {
  selectedResult: string;
}

interface IMultipleResultsTableDispatchProps {
  select: (metricId: string) => void;
}

const MultipleResultsTable = ({ results, select, selectedResult }: IMultipleResultsTableOwnProps & IMultipleResultsTableStateProps & IMultipleResultsTableDispatchProps) => {
  const tagKeys = chain(results)
    .flatMap(r => Object.keys(r.tags || {}))
    .uniq()
    .value();

  let columns: ITableColumn<ICanaryAnalysisResult>[] = tagKeys.map(key => ({
    label: key,
    width: 1,
    getContent: (result: ICanaryAnalysisResult) => <span>{result.tags[key]}</span>,
  }));

  columns = columns.concat({
    width: 1,
    getContent: (result: ICanaryAnalysisResult) => (
      <MetricResultClassification
        className="pull-right"
        classification={result.classification}
      />
    ),
  });

  return (
    <Table
      rows={results}
      columns={columns}
      className="multiple-results-table"
      rowClassName={r => classNames('horizontal', { selected: r.id === selectedResult })}
      rowKey={r => Object.entries(r.tags || {}).map(([key, value]) => `${key}:${value}`).join(':')}
      onRowClick={r => select(r.id)}
    />
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  selectedResult: selectedMetricResultIdSelector(state),
});

const mapDispatchToProps = (
  dispatch: Dispatch<ICanaryState>,
  ownProps: IMultipleResultsTableOwnProps
) => ({
  ...ownProps,
  select: (metricId: string) =>
    dispatch(Creators.selectReportMetric({ metricId })),
});

export default connect(mapStateToProps, mapDispatchToProps)(MultipleResultsTable);
