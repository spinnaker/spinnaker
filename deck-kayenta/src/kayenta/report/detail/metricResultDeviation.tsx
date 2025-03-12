import * as React from 'react';

interface IMetricResultDeviationProps {
  className?: string;
  ratio: number;
}

const formatDeviationAsPercentage = (ratio: number) => {
  if (typeof ratio !== 'number') {
    return 'N/A';
  } else if (ratio === 1) {
    return '0%';
  } else if (ratio < 1) {
    return ((ratio - 1) * 100).toFixed(1) + '%';
  } else {
    return '+' + ((ratio - 1) * 100).toFixed(1) + '%';
  }
};

export default ({ ratio, className }: IMetricResultDeviationProps) =>
  ratio && (
    <span className={className} style={{ color: 'var(--color-text-caption' }}>
      {formatDeviationAsPercentage(ratio)}
    </span>
  );
