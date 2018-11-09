import * as React from 'react';
import { FieldProps } from 'formik';

import { Validator } from './Validation';
export type IFieldValidationStatus = 'error' | 'warning' | 'message';

/** These props are used by FormField and FormikFormField components */
export interface IFieldLayoutPropsWithoutInput extends IValidationProps {
  required?: boolean;
  label?: React.ReactNode;
  help?: React.ReactNode;
  actions?: React.ReactNode;
}

/** These props are used by FieldLayout components, such as StandardFieldLayout */
export interface IFieldLayoutProps extends IFieldLayoutPropsWithoutInput {
  input: React.ReactNode;
}

/** These props are used by controlled components, such as <input> or Input components like TextInput */
export interface IControlledInputProps {
  value: FieldProps['field']['value'];
  onChange: FieldProps['field']['onChange'];
  onBlur: FieldProps['field']['onBlur'];
  name: FieldProps['field']['name'];
}

/** These props are used by Input components, such as TextInput */
export interface IValidationProps {
  touched?: boolean;
  validationMessage?: React.ReactNode;
  validationStatus?: IFieldValidationStatus;
  addValidator?: (validator: Validator) => void;
  removeValidator?: (validator: Validator) => void;
}

/** These props are used by Input components, such as TextInput */
export interface IFormInputProps {
  field: IControlledInputProps;
  validation: IValidationProps;
  inputClassName?: string;
}

/** These props are used by FormFields such as FormikFormField and FormField */
export interface ICommonFormFieldProps {
  input: React.ComponentType<IFormInputProps>;
  layout?: React.ComponentType<IFieldLayoutProps>;
}
