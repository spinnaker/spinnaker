import * as React from 'react';

import { IRegion } from 'core/account/AccountService';
import { IFormInputProps } from '../presentation';

export interface IRegionSelectInputProps extends IFormInputProps {
  account: string;
  readOnly?: boolean;
  regions: IRegion[];
}

export class RegionSelectInput extends React.Component<IRegionSelectInputProps> {
  public render() {
    const { account, readOnly, regions, value, onChange, ...otherProps } = this.props;
    if (!account) {
      return <div>(Select an account)</div>;
    }

    if (readOnly) {
      return <p className="form-control-static">{value}</p>;
    }

    return (
      <select className="form-control input-sm" value={value || ''} onChange={onChange} required={true} {...otherProps}>
        <option value="" disabled={true}>
          Select...
        </option>
        {regions.map(region => {
          return (
            <option key={region.name} value={region.name}>
              {region.name} {region.deprecated ? "(deprecated in the '" + account + "' account)" : ''}
            </option>
          );
        })}
      </select>
    );
  }
}
