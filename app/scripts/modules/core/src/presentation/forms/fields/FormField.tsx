import * as React from 'react';

import { noop } from 'core/utils';

import {
  ICommonFormFieldProps,
  IControlledInputProps,
  IFieldLayoutPropsWithoutInput,
  IValidationProps,
} from '../interface';
import { StandardFieldLayout } from '../layouts';
import { renderContent } from './renderContent';

export interface IFormFieldValidationProps {
  validate?: (value: any) => string;
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

  public render() {
    const { input, layout } = this.props; // ICommonFormFieldProps

    const { onChange, onBlur, value, name } = this.props; // IControlledInputProps
    const controlledInputProps: IControlledInputProps = { onChange, onBlur, value, name };

    const { touched, validationMessage: message, validationStatus: status } = this.props; // IValidationProps
    const validationMessage = message || this.props.validate(value);
    const validationStatus = status || !!validationMessage ? 'error' : null;
    const validationProps: IValidationProps = { touched, validationMessage, validationStatus };

    const { label, help, required, actions } = this.props; // IFieldLayoutPropsWithoutInput
    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };

    const inputElement = renderContent(input, { field: controlledInputProps, validation: validationProps });
    return renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement });
  }
}
