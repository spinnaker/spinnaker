import * as React from 'react';

import { MetricClassificationLabel } from 'kayenta/domain/MetricClassificationLabel';
import { mapMetricClassificationToColor } from './colors';

interface IMetricResultClassificationProps {
  classification: MetricClassificationLabel;
}

const buildStyle = (classification: MetricClassificationLabel) => ({
  backgroundColor: mapMetricClassificationToColor(classification),
});

const TEXT_COLOR = 'var(--color-text-on-dark)';

export default ({ classification }: IMetricResultClassificationProps) => (
  <div className="pill metric-result-classification" style={buildStyle(classification)}>
    <span style={{color: TEXT_COLOR}}>{classification}</span>
  </div>
);
