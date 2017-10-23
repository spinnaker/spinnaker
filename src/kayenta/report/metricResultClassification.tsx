import * as React from 'react';

import { MetricClassificationLabel } from 'kayenta/domain/MetricClassificationLabel';
import { mapMetricClassificationToColor } from './colors';

interface IMetricResultClassificationProps {
  classification: MetricClassificationLabel;
}

const buildStyle = (classification: MetricClassificationLabel) => ({
  backgroundColor: mapMetricClassificationToColor(classification),
});

export default ({ classification }: IMetricResultClassificationProps) => (
  <section style={buildStyle(classification)}>
    {classification}
  </section>
);
