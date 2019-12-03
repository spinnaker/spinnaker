import React from 'react';

export function LabeledValue(props: { label: string; value: React.ReactNode }) {
  return (
    <>
      <dt>{props.label}</dt>
      <dd>{props.value}</dd>
    </>
  );
}
