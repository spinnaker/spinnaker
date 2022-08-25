import _ from 'lodash';
import React from 'react';
import type { Option } from 'react-select';
import Select from 'react-select';

import { HelpField } from '@spinnaker/core';

import { AmazonInstanceTypeInfoRenderer } from './AmazonInstanceTypeInfoRenderer';
import type { IAmazonInstanceType } from '../../../../../instance/awsInstanceType.service';

export interface InstanceTypeSelectProps {
  availableInstanceTypesList: IAmazonInstanceType[];
  addOrUpdateInstanceType: (type: string, weight: string) => void;
}

export function InstanceTypeSelect(props: InstanceTypeSelectProps) {
  /**
   * Filters implemented:
   * - instance family / size / type e.g. c3/ large/ c3.large
   * - min vcpu e.g. 16vcpu
   * - min memory e.g 32gib
   * - instance storage type e.g. ssd, hdd
   * - spot support
   * - ebs support
   * - gpu support
   * - current generation v/s old generation like 'currentGen' / 'oldGen'
   */
  const filterOption = (option: IAmazonInstanceType, inputValue: string) => {
    const {
      name,
      defaultVCpus,
      memoryInGiB,
      instanceStorageInfo,
      ebsInfo,
      gpuInfo,
      supportedUsageClasses,
      currentGeneration,
    } = option;

    return inputValue.split(',').every((inputVal: string) => {
      const searchVal = _.toLower(inputVal.trim());
      return (
        (searchVal.match(/\d+\s*vcpu/) && defaultVCpus >= _.toNumber(searchVal.split('vcpu')[0].trim())) ||
        (searchVal.match(/\d+\s*gib/) && memoryInGiB >= _.toNumber(searchVal.split('gib')[0].trim())) ||
        (searchVal.match(/\b(ssd|hdd)\b/) && instanceStorageInfo?.storageTypes.includes(searchVal)) ||
        (searchVal === 'spot' && supportedUsageClasses?.includes(searchVal)) ||
        (searchVal === 'ebs' && ebsInfo) ||
        (searchVal === 'gpu' && gpuInfo) ||
        (searchVal === 'currentgen' && currentGeneration) ||
        (searchVal === 'oldgen' && !currentGeneration) ||
        name.match(searchVal)
      );
    });
  };

  const optionRenderer = (option: IAmazonInstanceType) => {
    return <AmazonInstanceTypeInfoRenderer instanceType={option} />;
  };

  return (
    <div className={'custom-profile'}>
      <Select
        className={`select`}
        clearable={false}
        multi={false}
        placeholder={'Filter like 16vcpu, 32gib, spot, oldGen or Select an instance type to add'}
        removeSelected={true}
        searchable={true}
        options={props.availableInstanceTypesList}
        optionRenderer={optionRenderer}
        filterOption={filterOption}
        valueRenderer={(o) => <>{o.name}</>}
        onChange={(o: Option<IAmazonInstanceType>) => props.addOrUpdateInstanceType(o.name, undefined)}
      />{' '}
      <HelpField id="aws.serverGroup.instanceTypesSelect" />
    </div>
  );
}
