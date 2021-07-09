import { startCase } from 'lodash';
import React from 'react';

import { ICapacity } from '../../index';

export interface IMinMaxDesiredChangesProps {
  current: ICapacity;
  next: ICapacity;
}

export function MinMaxDesiredChanges(props: IMinMaxDesiredChangesProps) {
  const { current, next } = props;
  const fields: Array<keyof ICapacity> = ['min', 'max', 'desired'];

  const changedCapacityFields = fields
    .map((field) => {
      const hasChanged = current[field] !== next[field];
      return hasChanged ? { field, currentValue: current[field], nextValue: next[field] } : null;
    })
    .filter((x) => !!x);

  if (!changedCapacityFields.length) {
    return <span>No changes</span>;
  }

  return (
    <>
      {changedCapacityFields.map((field) => (
        <div key={field.field}>
          {startCase(field.field)}: <b>{field.currentValue}</b> <i className="fa fa-long-arrow-alt-right" />{' '}
          <b>{field.nextValue}</b>
        </div>
      ))}
    </>
  );
}
