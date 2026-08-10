import React from 'react';

export interface ICanaryStatusProps {
  status?: string;
}

export function CanaryStatus({ status }: ICanaryStatusProps) {
  const statusLabel =
    status === 'LAUNCHED'
      ? 'launched'
      : status === 'RUNNING'
      ? 'running'
      : status === 'SUCCEEDED'
      ? 'succeeded'
      : status === 'FAILED'
      ? 'failed'
      : status === 'TERMINATED'
      ? 'terminated'
      : status === 'CANCELED'
      ? 'canceled'
      : 'unknown';

  return <span className={`label label-default label-${statusLabel}`}>{status}</span>;
}
