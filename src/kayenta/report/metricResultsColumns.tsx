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
    getContent: ({ results }) => (
      <div className="pull-right">
        <MetricResultClassification classification={results[0].classification}/>
      </div>
    ),
    width: 1,
  }
];

