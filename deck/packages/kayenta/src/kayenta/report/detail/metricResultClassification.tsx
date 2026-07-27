import classNames from 'classnames';
import * as React from 'react';

import { mapMetricClassificationToColor } from './colors';
import type { MetricClassificationLabel } from '../../domain/MetricClassificationLabel';

interface IMetricResultClassificationProps {
  classification: MetricClassificationLabel;
  className?: string;
}

const buildStyle = (classification: MetricClassificationLabel) => ({
  backgroundColor: mapMetricClassificationToColor(classification),
});

const TEXT_COLOR = 'var(--color-text-on-dark)';

export default ({ classification, className }: IMetricResultClassificationProps) => (
  <div className={classNames('pill', 'metric-result-classification', className)} style={buildStyle(classification)}>
    <span style={{ color: TEXT_COLOR }}>{classification}</span>
  </div>
);
