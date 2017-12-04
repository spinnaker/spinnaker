import * as React from 'react';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import ChangeMetricGroup from './changeMetricGroup';

export interface IMetricDetailProps {
  metric: ICanaryMetricConfig;
  showGroups: boolean;
  edit: (event: any) => void;
  remove: (event: any) => void;
}

/*
 * Configures all the available settings for a single metric.
 */
export default function MetricDetail({ metric, showGroups, edit, remove }: IMetricDetailProps) {
  return (
    <section className="horizontal metric-list-row">
      <div className="flex-6">
        {metric.name || '(new)'}
      </div>
      <div className="flex-3">
        {showGroups && metric.groups.join(', ')}
      </div>
      <div className="flex-1 horizontal center">
        <i
          className="fa fa-edit"
          data-id={metric.id}
          onClick={edit}
        />
        <ChangeMetricGroup metric={metric}/>
        <i
          className="fa fa-trash"
          data-id={metric.id}
          onClick={remove}
        />
      </div>
    </section>
  );
}
