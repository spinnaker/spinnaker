import * as React from 'react';
import memoizeOne from 'memoize-one';

import { orEmptyString, validationClassName } from './utils';
import { composeValidators, Validation, Validator } from '../Validation';
import { IFormInputProps, OmitControlledInputPropsFrom } from '../interface';

import './NumberInput.css';

interface INumberInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
}

const isNumber = (val: any): val is number => typeof val === 'number';

export class NumberInput extends React.Component<INumberInputProps> {
  private getMinValidator = memoizeOne((min: any) => (isNumber(min) ? Validation.minValue(min) : undefined));
  private getMaxValidator = memoizeOne((max: any) => (isNumber(max) ? Validation.maxValue(max) : undefined));

  private validator: Validator = (val: any, label?: string) => {
    const { min, max } = this.props;
    const validator = composeValidators([this.getMinValidator(min), this.getMaxValidator(max)]);
    return validator ? validator(val, label) : null;
  };

  public componentDidMount() {
    this.props.validation.addValidator(this.validator);
  }

  public componentWillUnmount() {
    this.props.validation.removeValidator(this.validator);
  }

  public render() {
    const { value, validation, inputClassName, ...otherProps } = this.props;
    const className = `NumberInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;
    return <input className={className} type="number" value={orEmptyString(value)} {...otherProps} />;
  }
}
