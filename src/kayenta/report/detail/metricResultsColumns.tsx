import * as React from 'react';
import { ITableColumn } from 'kayenta/layout/table';
import { IMetricResultsTableRow } from './metricResultsList';
import MetricResultClassification from './metricResultClassification';
import MetricResultDeviation from './metricResultDeviation';

export const metricResultsColumns: ITableColumn<IMetricResultsTableRow>[] = [
  {
    label: 'metric name',
    getContent: ({ metricName }) => <span>{metricName}</span>,
    width: 5,
  },
  {
    label: 'deviation',
    getContent: ({ results }) =>
      results[0].resultMetadata && <MetricResultDeviation ratio={results[0].resultMetadata.ratio} />,
    width: 1,
  },
  {
    label: 'result',
    getContent: ({ results }) => <MetricResultClassification classification={results[0].classification} />,
    width: 1,
  },
];
