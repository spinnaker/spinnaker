import * as React from 'react';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import FormRow from '../layout/formRow';

interface IMetricDetailProps {
  id: any;
  metric: ICanaryMetricConfig;
  changeName: any;
}

/*
 * Configures all the available settings for a single metric.
 */
export default function MetricDetail({ id, metric, changeName }: IMetricDetailProps) {
  return (
    <form role="form" className="form-horizontal container-fluid">
      <FormRow label="Name">
        <input type="text" className="form-control" value={metric.name} data-id={id} onChange={changeName}/>
      </FormRow>
      <FormRow label="Service">
        <input type="text" disabled={true} className="form-control" value={metric.serviceName} data-id={id}/>
      </FormRow>
    </form>
  );
}
