import React from 'react';

export function CurrentCapacity(props: { currentCapacity: number }) {
  return (
    <>
      <dt>Current</dt>
      <dd>{props.currentCapacity}</dd>
    </>
  );
}
