import * as React from 'react';

import { orEmptyString, validationClassName } from './utils';
import { IFormInputProps } from '../interface';

import './NumberInput.css';

interface INumberInputProps extends IFormInputProps, React.InputHTMLAttributes<any> {
  inputClassName?: string;
}

export const NumberInput = (props: INumberInputProps) => {
  const { field, validation, inputClassName, ...otherProps } = props;
  const fieldProps = { ...field, value: orEmptyString(field.value) };
  const className = `NumberInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  return <input className={className} type="number" {...fieldProps} {...otherProps} />;
};
