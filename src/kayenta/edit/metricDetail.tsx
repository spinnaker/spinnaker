import * as React from 'react';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import FormRow from '../layout/formRow';

interface IMetricDetailProps {
  metric: ICanaryMetricConfig;
  changeName: any;
}

/*
 * Configures all the available settings for a single metric.
 */
export default function MetricDetail({ metric, changeName }: IMetricDetailProps) {
  return (
    <form role="form" className="form-horizontal container-fluid">
      <FormRow label="Name">
        <input
          type="text"
          className="form-control"
          value={metric.name}
          data-id={metric.id}
          onChange={changeName}
        />
      </FormRow>
      <FormRow label="Service">
        <input
          type="text"
          className="form-control"
          value={metric.serviceName}
          data-id={metric.id}
          disabled={true}
        />
      </FormRow>
      <FormRow label="Groups">
        {/* TODO: needs to be a multiselect combo box like select2 */}
        <input
          type="text"
          className="form-control"
          value={metric.groups.join(',')}
          data-id={metric.id}
          disabled={true}
        />
      </FormRow>
    </form>
  );
}
