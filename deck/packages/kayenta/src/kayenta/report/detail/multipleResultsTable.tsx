import classNames from 'classnames';
import { chain } from 'lodash';
import * as React from 'react';
import type { Dispatch } from 'react-redux';
import { connect } from 'react-redux';

import { BreakString } from '@spinnaker/core';

import * as Creators from '../../actions/creators';
import type { ICanaryAnalysisResult } from '../../domain/ICanaryJudgeResult';
import type { ITableColumn } from '../../layout/table';
import { Table } from '../../layout/table';
import MetricResultClassification from './metricResultClassification';
import MetricResultDeviation from './metricResultDeviation';
import type { ICanaryState } from '../../reducers';
import { selectedMetricResultIdSelector } from '../../selectors';

interface IMultipleResultsTableOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IMultipleResultsTableStateProps {
  selectedResult: string;
}

interface IMultipleResultsTableDispatchProps {
  select: (metricId: string) => void;
}

const MultipleResultsTable = ({
  results,
  select,
  selectedResult,
}: IMultipleResultsTableOwnProps & IMultipleResultsTableStateProps & IMultipleResultsTableDispatchProps) => {
  const tagKeys = chain(results)
    .flatMap((r) => Object.keys(r.tags || {}))
    .uniq()
    .value();

  let columns: Array<ITableColumn<ICanaryAnalysisResult>> = tagKeys.map((key) => ({
    label: key,
    width: 5,
    getContent: (result: ICanaryAnalysisResult) => <BreakString>{result.tags[key]}</BreakString>,
  }));

  columns = columns.concat([
    {
      width: 1,
      getContent: ({ resultMetadata }) => resultMetadata && <MetricResultDeviation ratio={resultMetadata.ratio} />,
    },
    {
      width: 1,
      getContent: ({ classification }) => <MetricResultClassification classification={classification} />,
    },
  ]);

  return (
    <Table
      rows={results}
      columns={columns}
      className="multiple-results-table"
      headerClassName="sticky-header-2"
      rowClassName={(r) => classNames('horizontal', { selected: r.id === selectedResult })}
      rowKey={(r) =>
        Object.entries(r.tags || {})
          .map(([key, value]) => `${key}:${value}`)
          .join(':')
      }
      onRowClick={(r) => select(r.id)}
    />
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  selectedResult: selectedMetricResultIdSelector(state),
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>, ownProps: IMultipleResultsTableOwnProps) => ({
  ...ownProps,
  select: (metricId: string) => dispatch(Creators.selectReportMetric({ metricId })),
});

export default connect(mapStateToProps, mapDispatchToProps)(MultipleResultsTable);
