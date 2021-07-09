import { $rootScope } from 'ngimport';
import React from 'react';

import { RegionSelectInput } from './RegionSelectInput';
import { IRegion } from '../account';

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
    const { component, onChange, field } = this.props;
    component[field] = event.target.value;
    onChange(event.target.value);
    $rootScope.$apply(); // force re-digest
    this.setState({}); // force re-render
  }

  public render() {
    const { labelColumns, fieldColumns, account, regions, readOnly, component, field } = this.props;

    return (
      <div className="form-group">
        <div className={`col-md-${labelColumns} sm-label-right`}>Region</div>
        <div className={`col-md-${fieldColumns || 7}`}>
          <RegionSelectInput
            account={account}
            regions={regions}
            readOnly={readOnly}
            value={component[field]}
            onChange={(evt: any) => this.handleChange(evt)}
          />
        </div>
      </div>
    );
  }
}
