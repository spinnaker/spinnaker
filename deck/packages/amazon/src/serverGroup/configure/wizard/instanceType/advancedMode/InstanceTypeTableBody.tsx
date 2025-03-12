import { difference, flatten, keyBy } from 'lodash';
import React from 'react';
import type { SortEnd } from 'react-sortable-hoc';
import { SortableContainer, SortableElement } from 'react-sortable-hoc';

import type { IInstanceTypeFamily } from '@spinnaker/core';

import { InstanceTypeRow } from './InstanceTypeRow';
import type {
  IAmazonInstanceType,
  IAmazonPreferredInstanceType,
} from '../../../../../instance/awsInstanceType.service';
import type { IAmazonInstanceTypeOverride } from '../../../serverGroupConfiguration.service';

export function InstanceTypeTableBody(props: {
  isCustom: boolean;
  profileFamiliesDetails?: IInstanceTypeFamily[];
  selectedInstanceTypesMap: Map<string, IAmazonInstanceTypeOverride>;
  selectedInstanceTypesInfo?: IAmazonInstanceType[];
  addOrUpdateInstanceType: (instanceType: string, weight: string) => void;
  removeInstanceType: (instanceType: string) => void;
  handleSortEnd: (sortEnd: SortEnd) => void;
}) {
  return (
    <TableRows
      isCustom={props.isCustom}
      selectedInstanceTypesMap={props.selectedInstanceTypesMap}
      selectedInstanceTypesInfo={props.selectedInstanceTypesInfo}
      removeInstanceType={props.removeInstanceType}
      addOrUpdateInstanceType={props.addOrUpdateInstanceType}
      instanceTypeDetails={
        props.isCustom
          ? null
          : new Map(
              Object.entries(
                keyBy(flatten(props.profileFamiliesDetails.map((f: IInstanceTypeFamily) => f.instanceTypes)), 'name'),
              ),
            )
      }
      onSortEnd={(sortEnd) => props.handleSortEnd(sortEnd)}
      distance={1}
    />
  );
}

const TableRows = SortableContainer(
  (props: {
    isCustom: boolean;
    instanceTypeDetails?: Map<string, IAmazonPreferredInstanceType>;
    selectedInstanceTypesMap: Map<string, IAmazonInstanceTypeOverride>;
    selectedInstanceTypesInfo?: IAmazonInstanceType[];
    removeInstanceType: (typeToRemove: string) => void;
    addOrUpdateInstanceType: (instanceType: string, weight: string) => void;
  }) => {
    const { isCustom, selectedInstanceTypesMap } = props;

    let selectedRows, unselectedRows;
    if (isCustom) {
      selectedRows =
        selectedInstanceTypesMap.size > 0 &&
        Array.from(selectedInstanceTypesMap.values())
          .sort((i1, i2) => i1.priority - i2.priority)
          .map((selectedType, index: number) => (
            <SortableRow
              key={`${selectedType.instanceType}-${index}`}
              index={index}
              isCustom={true}
              selectedType={selectedType}
              selectedTypeInfo={props.selectedInstanceTypesInfo.find((it) => it.name === selectedType.instanceType)}
              removeInstanceType={props.removeInstanceType}
              addOrUpdateInstanceType={props.addOrUpdateInstanceType}
            />
          ));
    } else {
      const { instanceTypeDetails } = props;
      const instanceTypesInProfile: string[] = Array.from(instanceTypeDetails?.keys());
      const unselectedInstanceTypes: string[] = difference(
        instanceTypesInProfile,
        Array.from(selectedInstanceTypesMap.keys()),
      );

      const selectedRowsOrdered: IAmazonInstanceTypeOverride[] = Array.from(selectedInstanceTypesMap.values())
        .filter((selectedType: IAmazonInstanceTypeOverride) =>
          instanceTypesInProfile.includes(selectedType.instanceType),
        )
        .sort((i1, i2) => i1.priority - i2.priority);

      selectedRows =
        selectedRowsOrdered &&
        selectedRowsOrdered.length > 0 &&
        selectedRowsOrdered.map((selectedType: IAmazonInstanceTypeOverride, index: number) => (
          <SortableRow
            key={`${selectedType.instanceType}-${index}`}
            index={index}
            isCustom={false}
            selectedType={selectedType}
            instanceTypeDetails={instanceTypeDetails}
            removeInstanceType={props.removeInstanceType}
            addOrUpdateInstanceType={props.addOrUpdateInstanceType}
          />
        ));

      unselectedRows =
        unselectedInstanceTypes &&
        unselectedInstanceTypes.length > 0 &&
        unselectedInstanceTypes.map((instanceType) => (
          <InstanceTypeRow
            key={instanceType}
            isCustom={false}
            instanceTypeDetails={instanceTypeDetails.get(instanceType)}
            addOrUpdateInstanceType={props.addOrUpdateInstanceType}
          />
        ));
    }

    return (
      <tbody>
        {selectedRows}
        {!isCustom ? unselectedRows : null}
      </tbody>
    );
  },
);

const SortableRow = SortableElement(
  (props: {
    isCustom: boolean;
    selectedType: IAmazonInstanceTypeOverride;
    selectedTypeInfo?: IAmazonInstanceType;
    instanceTypeDetails?: Map<string, IAmazonPreferredInstanceType>;
    removeInstanceType: (typeToRemove: string) => void;
    addOrUpdateInstanceType: (instanceType: string, weight: string) => void;
  }) => (
    <InstanceTypeRow
      key={props.selectedType.instanceType}
      isCustom={props.isCustom}
      selectedType={props.selectedType}
      selectedTypeInfo={props.selectedTypeInfo}
      instanceTypeDetails={!props.isCustom ? props.instanceTypeDetails.get(props.selectedType.instanceType) : null}
      removeInstanceType={props.removeInstanceType}
      addOrUpdateInstanceType={props.addOrUpdateInstanceType}
    />
  ),
);
