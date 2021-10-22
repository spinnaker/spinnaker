import React from 'react';
import type { Option } from 'react-select';
import Select from 'react-select';

import { HelpField } from '@spinnaker/core';

export function Heading(props: { isCustom: boolean; profileLabel?: string; profileDescriptionArr?: string[] }) {
  let description;
  if (props.isCustom) {
    description = <p>Choose the instance types that best suit the needs of your application.</p>;
  } else {
    description = (
      <>
        <p>
          <b>{props.profileLabel}</b>
        </p>
        <ul>
          {props.profileDescriptionArr.map((d, index) => (
            <li key={index}>{d}</li>
          ))}
        </ul>
      </>
    );
  }

  return (
    <div className={'row sub-section'}>
      <h4>Instance Types</h4>
      <div className={'description'}>
        {description}
        <i>
          <b>Note:</b>
          <ul>
            <li>
              The order of instance types sets their priority when On-Demand capacity is launched; instance type at the
              top is prioritized the highest.
            </li>
            <li>Some instance types might not be available for the selected configuration.</li>
          </ul>
        </i>
      </div>
    </div>
  );
}

export function Header(props: { isCustom: boolean; showCpuCredits?: boolean }) {
  let emptyHeaders, instanceTypeHeader, otherHeaders, tailingEmptyHeader;
  if (props.isCustom) {
    emptyHeaders = <th />;
    instanceTypeHeader = (
      <th>
        Instance Type <HelpField id="aws.serverGroup.instanceTypes" />
      </th>
    );
    otherHeaders = null;
    tailingEmptyHeader = <th />;
  } else {
    emptyHeaders = (
      <>
        <th />
        <th />
      </>
    );
    instanceTypeHeader = <th>InstanceType</th>;
    otherHeaders = (
      <>
        <th>vCPU</th>
        <th>Mem (GiB)</th>
        {props.showCpuCredits && <th>CPU Credits</th>}
        <th>
          Storage (GB)
          <HelpField id={'aws.serverGroup.storageType'} />
        </th>
        <th>Cost</th>
      </>
    );
    tailingEmptyHeader = null;
  }

  return (
    <thead>
      <tr>
        {emptyHeaders}
        {instanceTypeHeader}
        {otherHeaders}
        <th>
          Weight <HelpField id="aws.serverGroup.instanceTypeWeight" />
        </th>
        {tailingEmptyHeader}
      </tr>
    </thead>
  );
}

export function Footer(props: {
  isCustom: boolean;
  availableInstanceTypesList: string[];
  addOrUpdateInstanceType: (type: string, weight: string) => void;
}) {
  return props.isCustom ? (
    <tfoot>
      <tr>
        <td>
          <span className={'glyphicon glyphicon-plus-sign'} style={{ paddingTop: '8px' }} />
        </td>
        <td colSpan={2}>
          <InstanceTypeSelect
            availableInstanceTypesList={props.availableInstanceTypesList}
            addOrUpdateInstanceType={props.addOrUpdateInstanceType}
          />
        </td>
        <td></td>
      </tr>
    </tfoot>
  ) : null;
}

const InstanceTypeSelect = (props: {
  availableInstanceTypesList: string[];
  addOrUpdateInstanceType: (type: string, weight: string) => void;
}): JSX.Element => {
  const instanceTypeListOptions = props.availableInstanceTypesList.map((instanceType) => {
    return { label: instanceType, value: instanceType };
  });

  return (
    <Select
      clearable={false}
      multi={false}
      placeholder={'Select an instance type to add...'}
      removeSelected={true}
      searchable={true}
      options={instanceTypeListOptions}
      onChange={(o: Option<string>) => props.addOrUpdateInstanceType(o.value, undefined)}
    />
  );
};
