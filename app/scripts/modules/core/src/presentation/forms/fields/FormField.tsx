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

export type IFormFieldProps = ICommonFormFieldProps &
  Partial<IControlledInputProps> &
  IFieldLayoutPropsWithoutInput &
  IValidationProps;

export class FormField extends React.Component<IFormFieldProps> {
  public static defaultProps: Partial<IFormFieldProps> = {
    layout: StandardFieldLayout,
    onBlur: noop,
    onChange: noop,
    name: null,
  };

  public render() {
    const { input, layout } = this.props; // ICommonFormFieldProps

    const { onChange, onBlur, value, name } = this.props; // IControlledInputProps
    const controlledInputProps: IControlledInputProps = { onChange, onBlur, value, name };

    const { touched, error, warning, preview } = this.props; // IValidationProps
    const validationProps: IValidationProps = { touched, error, warning, preview };

    const { label, help, required, actions } = this.props; // IFieldLayoutPropsWithoutInput
    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };

    const inputElement = renderContent(input, { field: controlledInputProps, validation: validationProps });
    return renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement });
  }
}
