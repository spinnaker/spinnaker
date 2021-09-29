import { get } from 'lodash';
import React from 'react';
import type { Option } from 'react-select';

import type { Application, ISelectInputProps, ISubnet, Omit } from '@spinnaker/core';
import { createFakeReactSyntheticEvent, SelectInput, useDeepObjectDiff, useMountStatusRef } from '@spinnaker/core';
import { AWSProviderSettings } from '../aws.settings';

export interface ISubnetSelectInputProps extends Omit<ISelectInputProps, 'options'> {
  value: string;
  onChange: (event: React.ChangeEvent<any>) => void;
  application: Application;
  readOnly?: boolean;
  hideClassic?: boolean;
  // Ordered list of subnet types to default to
  // higher priority defaults should be ordered first
  defaultSubnetTypes: string[];
  subnets: ISubnet[];
  region: string;
  credentials: string;
}

export interface ISubnetSelectInputState {
  options: Array<Option<string>>;
}

function isClassicLockout(region: string, credentials: string, application: Application): boolean {
  const { classicLaunchLockout, classicLaunchAllowlist: allowlist } = AWSProviderSettings;

  const appCreationDate = Number(get(application, 'attributes.createTs', 0));
  const appCreatedAfterLockout = appCreationDate > (classicLaunchLockout || 0);
  const isAllowlisted = !!allowlist && allowlist.some((e) => e.region === region && e.credentials === credentials);
  return appCreatedAfterLockout || !isAllowlisted;
}

function getOptions(subnets: ISubnet[], isClassicHidden: boolean): Array<Option<string>> {
  const sortByLabel = (a: ISubnet, b: ISubnet) => a.label.localeCompare(b.label);
  const asOption = (subnet: ISubnet): Option<string> => ({ value: subnet.purpose, label: subnet.label });

  const classicOption = isClassicHidden ? [] : [{ label: 'None (EC2 Classic)', value: '' } as Option];

  const activeOptions = subnets
    .filter((x) => !x.deprecated)
    .sort(sortByLabel)
    .map(asOption);

  const deprecatedOptions = subnets
    .filter((x) => x.deprecated)
    .sort(sortByLabel)
    .map(asOption);

  if (deprecatedOptions.length) {
    deprecatedOptions.unshift({ label: '-----------', value: '', disabled: true });
  }

  return classicOption.concat(activeOptions).concat(deprecatedOptions) as Array<Option<string>>;
}

function getDefaultSubnet(subnets: ISubnet[], defaultSubnetTypes: string[] = []): ISubnet {
  for (const subnetType of defaultSubnetTypes) {
    const defaultSubnet = subnets.find((subnet) => subnetType === subnet.purpose);
    if (defaultSubnet) {
      return defaultSubnet;
    }
  }
  return undefined;
}

export function SubnetSelectInput(props: ISubnetSelectInputProps) {
  const {
    application,
    credentials,
    defaultSubnetTypes,
    hideClassic,
    name,
    onChange,
    readOnly,
    region,
    subnets,
    value,
    ...otherProps
  } = props;

  // Allow subnets array reference to change by parent component
  const subnetsDiff = useDeepObjectDiff(subnets);

  const options = React.useMemo(() => {
    const isClassicHidden = hideClassic || isClassicLockout(region, credentials, application);
    return getOptions(subnets, isClassicHidden);
  }, [hideClassic, region, credentials, application, subnetsDiff]);

  const defaultSubnet = getDefaultSubnet(subnets, defaultSubnetTypes) || subnets[0];
  const mountStatus = useMountStatusRef().current;

  React.useEffect(() => {
    const isSelectionValid = options.some((o) => o.value === value);
    // - apply the default value whenever `options` changes and the current
    // selection is invalid, including on the first pass
    // - apply the default value on all subsequent renders if `options` changes
    const applyDefault = !isSelectionValid || mountStatus !== 'FIRST_RENDER';
    if (defaultSubnet && applyDefault) {
      onChange(createFakeReactSyntheticEvent({ name, value: defaultSubnet.purpose }));
    }
  }, [options]);

  if (readOnly) {
    return <p className="form-control-static">{props.value || 'None (EC2 Classic)'}</p>;
  }

  return <SelectInput options={options} value={value} onChange={onChange} {...otherProps} />;
}
