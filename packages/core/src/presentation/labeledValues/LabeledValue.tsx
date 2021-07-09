import React from 'react';
import { HelpField } from './../../help';

export function LabeledValue(props: { label: string; value: React.ReactNode; helpFieldId?: string }) {
  return (
    <>
      <dt>
        {props.label} {props.helpFieldId && <HelpField id={props.helpFieldId} />}
      </dt>
      <dd>{props.value}</dd>
    </>
  );
}
