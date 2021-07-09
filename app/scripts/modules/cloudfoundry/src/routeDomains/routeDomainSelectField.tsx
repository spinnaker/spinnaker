import React from 'react';

import { ICloudFoundryDomain } from '../domain';

export interface IRouteDomainSelectFieldProps {
  account: string;
  component: { [key: string]: any };
  field: string;
  fieldColumns?: number;
  labelColumns: number;
  onChange: (domain: string) => void;
  readOnly?: boolean;
  domains: ICloudFoundryDomain[];
}

export function RouteDomainSelectField(props: IRouteDomainSelectFieldProps) {
  return (
    <div className="form-group">
      <div className={`col-md-${props.labelColumns} sm-label-right`}>Domain</div>
      {!props.account && <div className={`col-md-${props.fieldColumns || 7}`}>(Select an account)</div>}
      <div className={`col-md-${props.fieldColumns || 7}`}>
        {props.account && !props.readOnly && (
          <select
            className="form-control input-sm"
            value={props.component[props.field]}
            onChange={(event) => {
              props.component[props.field] = event.target.value;
              props.onChange(event.target.value);
            }}
            required={true}
          >
            <option value="" disabled={true}>
              Select...
            </option>
            {props.domains.map((domain) => {
              return (
                <option key={domain.name} value={domain.name}>
                  {domain.name}
                </option>
              );
            })}
          </select>
        )}
        {props.readOnly && <p className="form-control-static">{props.component[props.field]}</p>}
      </div>
    </div>
  );
}
