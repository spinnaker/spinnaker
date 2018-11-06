import * as React from 'react';

import { noop } from 'core/utils';

import { Validation, ValidationFunction } from '../Validation';

import {
  ICommonFormFieldProps,
  IControlledInputProps,
  IFieldLayoutPropsWithoutInput,
  IValidationProps,
} from '../interface';
import { StandardFieldLayout } from '../layouts';
import { renderContent } from './renderContent';

export interface IFormFieldValidationProps {
  validate?: ValidationFunction | ValidationFunction[];
}

export type IFormFieldProps = IFormFieldValidationProps &
  ICommonFormFieldProps &
  Partial<IControlledInputProps> &
  IFieldLayoutPropsWithoutInput &
  IValidationProps;

export class FormField extends React.Component<IFormFieldProps> {
  public static defaultProps: Partial<IFormFieldProps> = {
    layout: StandardFieldLayout,
    validate: noop,
    onBlur: noop,
    onChange: noop,
    name: null,
  };

  /** Returns validation function composed of all the `validate` functions (and `isRequired` if `required` is truthy) */
  /** Returns validation function composed of all the `validate` functions (and `isRequired` if `required` is truthy) */
  private composedValidation(
    label: IFormFieldProps['label'],
    required: boolean,
    validate: IFormFieldProps['validate'],
  ): ValidationFunction {
    const labelStr = typeof label === 'string' ? label : 'This Field';
    const requiredFn = !!required && Validation.isRequired(`${labelStr} is required`);
    const validationFns = [requiredFn].concat(validate).filter(x => !!x);

    return validationFns.length ? Validation.compose(...validationFns) : null;
  }

  public render() {
    const { input, layout } = this.props; // ICommonFormFieldProps
    const { validate } = this.props; // IFormFieldValidationProps
    const { label, help, required, actions } = this.props; // IFieldLayoutPropsWithoutInput
    const { touched, validationMessage: message, validationStatus: status } = this.props; // IValidationProps
    const { onChange, onBlur, value, name } = this.props; // IControlledInputProps

    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };
    const controlledInputProps: IControlledInputProps = { onChange, onBlur, value, name };

    const validationMessage = message || this.composedValidation(label, required, validate)(value);
    const validationStatus = status || !!validationMessage ? 'error' : null;
    const validationProps: IValidationProps = { touched, validationMessage, validationStatus };

    const inputElement = renderContent(input, { field: controlledInputProps, validation: validationProps });
    return renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement });
  }
}
