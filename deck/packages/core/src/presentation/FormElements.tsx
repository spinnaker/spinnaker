import React from 'react';

import { Tooltip } from './Tooltip';

export const SpInput = (
  props: React.DetailedHTMLProps<React.InputHTMLAttributes<HTMLInputElement>, HTMLInputElement> & {
    error?: string;
    name: string;
  },
) => {
  const { error, ...inputProps } = props;
  if (error) {
    inputProps.className = `${inputProps.className || ''} invalid`;
  }
  inputProps.type = inputProps.type || 'text';
  return (
    <Tooltip id={inputProps.name} value={props.error} placement="right">
      <input {...inputProps} />
    </Tooltip>
  );
};

export const spelNumberCheck = (value: string | number, allowNegative?: boolean): string => {
  const invalidError = `Value must be a${!allowNegative ? ' positive ' : ' '}number or a SpEL expression.`;
  const numberValue = Number(value);

  if (Number.isNaN(numberValue)) {
    // if not a number, check if it's a spel expression
    const stringValue = String(value);
    const spelStart = stringValue ? stringValue.indexOf('${') : -1;
    if (spelStart !== -1 && stringValue.includes('}', spelStart)) {
      return null;
    }
    return invalidError;
  }

  return !allowNegative && numberValue < 0 ? invalidError : null;
};
