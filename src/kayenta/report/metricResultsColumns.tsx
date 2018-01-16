import * as React from 'react';
import MetricResultClassification from './metricResultClassification';
import { ITableColumn } from 'kayenta/layout/table';
import { IMetricResultsTableRow } from './metricResultsList';

export const metricResultsColumns: ITableColumn<IMetricResultsTableRow>[] = [
  {
    label: 'metric name',
    getContent: ({ metricName }) => (<section>{metricName}</section>),
    width: 5,
  },
  {
    label: 'result',
    labelClassName: 'text-center',
    getContent: ({ results }) => (
      <MetricResultClassification
        className="pull-right"
        classification={results[0].classification}
      />
    ),
    width: 1,
  }
];

