import * as React from 'react';

interface IMetricListHeaderProps {
  showGroups: boolean;
}

export default function MetricListHeader({ showGroups }: IMetricListHeaderProps) {
  return (
    <section className="horizontal metric-list-header">
      <div className="flex-3">
        <h6 className="heading-6 uppercase color-text-primary">Metric Name</h6>
      </div>
      <div className="flex-3">
        <h6 className="heading-6 uppercase color-text-primary">Query</h6>
      </div>
      <div className="flex-3">
        {showGroups && (<h6 className="heading-6 uppercase color-text-primary">Groups</h6>)}
      </div>
      <div className="flex-1"/>
    </section>
  );
}
