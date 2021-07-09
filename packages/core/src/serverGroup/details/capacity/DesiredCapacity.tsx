import React from 'react';
import { ICapacity } from '../../serverGroupWriter.service';

export function DesiredCapacity(props: { capacity: ICapacity; simpleMode: boolean }) {
  const SimpleCapacity = () => (
    <>
      <dt>Min/Max</dt>
      <dd>{props.capacity.desired}</dd>
    </>
  );

  const AdvancedCapacity = () => (
    <>
      <dt>Min</dt>
      <dd>{props.capacity.min}</dd>
      <dt>Desired</dt>
      <dd>{props.capacity.desired}</dd>
      <dt>Max</dt>
      <dd>{props.capacity.max}</dd>
    </>
  );

  return props.simpleMode ? <SimpleCapacity /> : <AdvancedCapacity />;
}
