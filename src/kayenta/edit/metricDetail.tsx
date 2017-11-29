import * as React from 'react';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import metricStoreConfigService from 'kayenta/metricStore/metricStoreConfig.service';
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
  const config = metricStoreConfigService.getDelegate(metric.query.type);
  const queryFinder = config && config.queryFinder ? config.queryFinder : (_metric: ICanaryMetricConfig) => `queryFinder not yet implemented for ${config.name}`;

  return (
    <section className="horizontal metric-list-row">
      <div className="flex-3">
        {metric.name || '(new)'}
      </div>
      <div className="flex-3">
        {queryFinder(metric)}
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
