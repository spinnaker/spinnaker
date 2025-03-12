import React, { useState } from 'react';
import { Checkbox } from 'react-bootstrap';
import { SortableHandle } from 'react-sortable-hoc';

import { HoverablePopover, TextInput, Tooltip } from '@spinnaker/core';

import { AmazonInstanceTypeInfoRenderer } from './AmazonInstanceTypeInfoRenderer';
import type { IAmazonPreferredInstanceType } from '../../../../../instance/awsInstanceType.service';
import type { IAmazonInstanceType } from '../../../../../instance/awsInstanceType.service';
import { CostFactor } from '../../../../../instance/details/CostFactor';
import type { IAmazonInstanceTypeOverride } from '../../../serverGroupConfiguration.service';

export interface IRowProps {
  isCustom: boolean;
  selectedType?: IAmazonInstanceTypeOverride;
  selectedTypeInfo?: IAmazonInstanceType;
  instanceTypeDetails?: IAmazonPreferredInstanceType;
  removeInstanceType?: (typeToRemove: string) => void;
  addOrUpdateInstanceType: (instanceType: string, weight: string) => void;
}

export function InstanceTypeRow(props: IRowProps) {
  const isRowSelected = props.selectedType ? true : false;
  const [newWeightedCap, setNewWeightedCap] = useState('');
  const instanceType = isRowSelected ? props.selectedType.instanceType : props.instanceTypeDetails.name;
  const disableRow = isRowSelected ? false : props.instanceTypeDetails.unavailable;

  const selectOrUpdateRow = (instanceType: string, weight: string) => {
    if (!disableRow) {
      props.addOrUpdateInstanceType(instanceType, weight);
    }
    setNewWeightedCap('');
  };

  const handleWeightChange = (e: React.ChangeEvent<any>): void => {
    setNewWeightedCap(e.target.value);
    if (isRowSelected) {
      selectOrUpdateRow(instanceType, e.target.value);
    } else {
      document.getElementById(`selectInstanceType-${instanceType}`).focus();
    }
  };

  let row;
  if (props.isCustom) {
    const instanceTypeInfo = <AmazonInstanceTypeInfoRenderer instanceType={props.selectedTypeInfo} />;
    row = (
      <tr key={instanceType} className={'sortable clickable'}>
        <td>
          <DragHandle />
        </td>
        <td>
          {`${instanceType} `}
          <HoverablePopover placement={'right'} template={instanceTypeInfo} className={'custom-profile'}>
            <span className="clickable help-field">
              <i className="small glyphicon glyphicon-info-sign" />
            </span>
          </HoverablePopover>
        </td>
        <td title={'Enter optional weight (allowed values: 1 to 999).'}>
          <TextInput
            className={'form-control input input-sm'}
            pattern="[0-9]*"
            placeholder="Enter optional weight (allowed values: 1 to 999)."
            value={props.selectedType?.weightedCapacity || ''}
            onChange={(e) => selectOrUpdateRow(instanceType, e.target.value)}
          />
        </td>
        <td>
          <div>
            <span>
              <a
                className="btn btn-sm btn-link clickable"
                onClick={() => props.removeInstanceType(instanceType)}
                style={{ padding: '5px' }}
              >
                <Tooltip value={'Remove instance type'}>
                  <span className="glyphicon glyphicon-trash" />
                </Tooltip>
              </a>
            </span>
          </div>
        </td>
      </tr>
    );
  } else {
    const { cpu, memory, cpuCreditsPerHour, storage, costFactor } = props.instanceTypeDetails;
    row = (
      <tr
        key={instanceType}
        className={isRowSelected ? 'sortable clickable' : `non-sortable ${disableRow ? 'unavailable' : 'clickable'}`}
        title={
          isRowSelected
            ? 'Click to unselect instance type'
            : disableRow
            ? 'This instance type is not available for the selected configuration'
            : 'Click to select instance type'
        }
        onClick={(e) => {
          if (!$(e.target).is('input')) {
            isRowSelected ? props.removeInstanceType(instanceType) : selectOrUpdateRow(instanceType, newWeightedCap);
          }
        }}
      >
        {isRowSelected ? (
          <td>
            <DragHandle />
          </td>
        ) : (
          <td></td>
        )}
        <td>
          <Checkbox
            id={`selectInstanceType-${instanceType}`}
            checked={isRowSelected ? true : false}
            disabled={disableRow}
            onChange={() => {
              isRowSelected ? props.removeInstanceType(instanceType) : selectOrUpdateRow(instanceType, newWeightedCap);
            }}
          />
        </td>
        <td>{instanceType}</td>
        <td>{cpu}</td>
        <td>{memory}</td>
        {cpuCreditsPerHour ? (
          <td>{cpuCreditsPerHour}</td>
        ) : (
          <td title={'Cpu credits not applicable to instance type.'}>-</td>
        )}
        {storage.type === 'EBS' && <td>EBS Only</td>}
        {storage.type === 'SSD' && <td>{storage.count + 'x' + storage.size}</td>}
        <td>
          <CostFactor min={costFactor} />
        </td>
        <td title={!disableRow ? 'Enter optional weight (allowed values: 1 to 999).' : ''}>
          <TextInput
            inputClassName={'form-control input input-sm'}
            id={`weightedCapacity-${instanceType}`}
            pattern="[0-9]*"
            placeholder="Enter optional weight (allowed values: 1 to 999)."
            disabled={disableRow}
            value={props.selectedType?.weightedCapacity || newWeightedCap || ''}
            onChange={handleWeightChange}
          />
        </td>
      </tr>
    );
  }

  return row;
}

const DragHandle = SortableHandle(() => (
  <Tooltip value={'Drag to change priority'}>
    <span className="instance-type-drag-handle glyphicon glyphicon-resize-vertical" />
  </Tooltip>
));
