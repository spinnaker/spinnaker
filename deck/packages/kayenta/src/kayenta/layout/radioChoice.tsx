import * as React from 'react';

import { DISABLE_EDIT_CONFIG, DisableableInput } from './disableable';

export interface IRadioChoiceProps {
  value: string;
  label: string | JSX.Element;
  name: string;
  current: string;
  action: (event: any) => void;
  metricId?: string;
}

export default function RadioChoice({ value, label, name, current, action, metricId }: IRadioChoiceProps) {
  return (
    <div className="radio-inline">
      <label style={{ fontWeight: 'normal', marginRight: '1em' }}>
        <DisableableInput
          type="radio"
          name={name}
          value={value}
          data-id={metricId}
          onChange={action}
          checked={value === current}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />{' '}
        {label}
      </label>
    </div>
  );
}
