import { ITableColumn } from 'kayenta/layout/table';
import * as React from 'react';

import { BreakString } from '@spinnaker/core';

import MetricResultClassification from './metricResultClassification';
import MetricResultDeviation from './metricResultDeviation';
import { IMetricResultsTableRow } from './metricResultsList';

export const metricResultsColumns: Array<ITableColumn<IMetricResultsTableRow>> = [
  {
    label: 'metric name',
    getContent: ({ metricName }) => <BreakString>{metricName}</BreakString>,
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
