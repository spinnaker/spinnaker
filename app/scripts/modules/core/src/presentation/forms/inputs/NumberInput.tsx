import React from 'react';

import { useInternalValidator } from './hooks/useInternalValidator.hook';
import { IFormInputProps, OmitControlledInputPropsFrom } from './interface';
import { orEmptyString, validationClassName } from './utils';
import { composeValidators, IValidator, Validators } from '../validation';

import './NumberInput.css';

interface INumberInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
}

const isNumber = (val: any): val is number => typeof val === 'number';

export function NumberInput(props: INumberInputProps) {
  const { value, validation, inputClassName, ...otherProps } = props;

  const minMaxValidator: IValidator = (val: any, label?: string) => {
    const minValidator = isNumber(props.min) ? Validators.minValue(props.min) : undefined;
    const maxValidator = isNumber(props.max) ? Validators.maxValue(props.max) : undefined;
    const validator = composeValidators([minValidator, maxValidator]);
    return validator ? validator(val, label) : null;
  };

  useInternalValidator(validation, minMaxValidator);

  const className = `NumberInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;
  return <input className={className} type="number" value={orEmptyString(value)} {...otherProps} />;
}
