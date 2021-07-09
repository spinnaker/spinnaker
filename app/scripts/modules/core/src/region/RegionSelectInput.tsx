import React from 'react';
import { Option } from 'react-select';

import { IRegion } from '../account/AccountService';

import { IFormInputProps, SelectInput } from '../presentation';

export interface IRegionSelectInputProps extends IFormInputProps {
  account: string;
  readOnly?: boolean;
  regions: IRegion[];
}

export function RegionSelectInput(props: IRegionSelectInputProps) {
  const { account, readOnly, regions, ...otherProps } = props;

  if (!account) {
    return <div>(Select an account)</div>;
  } else if (readOnly) {
    return <p className="form-control-static">{props.value}</p>;
  }

  const allRegions: IRegion[] = regions ? regions : [];

  const options: Array<Option<string>> = allRegions.map((region) => ({
    value: region.name,
    label: `${region.name}${region.deprecated ? " (deprecated in the '" + account + "' account)" : ''}`,
  }));

  options.unshift({ label: 'Select...', value: '', disabled: true });

  return <SelectInput {...otherProps} className="form-control input-sm" options={options} />;
}
