import * as React from 'react';
import { $rootScope } from 'ngimport';

import { IRegion } from 'core/account/AccountService';

export interface IRegionSelectFieldProps {
  account: string;
  component: { [key: string]: any };
  field: string;
  fieldColumns?: number;
  labelColumns: number;
  onChange: (region: string) => void;
  readOnly?: boolean;
  regions: IRegion[];
}

export class RegionSelectField extends React.Component<IRegionSelectFieldProps> {
  private handleChange(event: React.ChangeEvent<HTMLSelectElement>) {
    const props = this.props;
    props.component[props.field] = event.target.value;
    props.onChange(event.target.value);
    $rootScope.$apply(); // force re-digest
    this.setState({}); // force re-render
  }

  public render() {
    const props = this.props;
    return (
      <div className="form-group">
        <div className={`col-md-${props.labelColumns} sm-label-right`}>Region</div>
        {!props.account && <div className={`col-md-${props.fieldColumns || 7}`}>(Select an account)</div>}
        <div className={`col-md-${props.fieldColumns || 7}`}>
          {props.account &&
            !props.readOnly && (
              <select
                className="form-control input-sm"
                value={props.component[props.field] || ''}
                onChange={event => this.handleChange(event)}
                required={true}
              >
                <option value="" disabled={true}>
                  Select...
                </option>
                {props.regions.map(region => {
                  return (
                    <option key={region.name} value={region.name}>
                      {region.name} {region.deprecated ? "(deprecated in the '" + props.account + "' account)" : ''}
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
}
