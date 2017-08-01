import * as React from 'react';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import FormRow from '../layout/formRow';

interface IMetricDetailProps {
  metric: ICanaryMetricConfig;
  edit: (event: any) => void;
  remove: (event: any) => void;
}

/*
 * Configures all the available settings for a single metric.
 */
export default function MetricDetail({ metric, edit, remove }: IMetricDetailProps) {
  return (
    <form role="form" className="form-horizontal container-fluid">
      <div className="col-md-11">
        <FormRow label="Name">
          <input
            type="text"
            className="form-control"
            value={metric.name}
            data-id={metric.id}
            disabled={true}
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
      </div>
      <div className="col-md-1">
        <i
          className="fa fa-edit"
          data-id={metric.id}
          onClick={edit}
        />
        <i
          className="fa fa-trash"
          data-id={metric.id}
          onClick={remove}
        />
      </div>
    </form>
  );
}
