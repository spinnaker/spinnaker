import * as React from 'react';
import { FieldProps } from 'formik';

import { IValidator } from './validation';
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

/**
 * These props are used by controlled components, such as <input> or Input components like TextInput
 * The typings reference the typings supplied by formik FieldProps
 */
export interface IControlledInputProps {
  value: FieldProps['field']['value'];
  onChange: FieldProps['field']['onChange'];
  onBlur: FieldProps['field']['onBlur'];
  name: FieldProps['field']['name'];
}

export type Omit<T, K> = Pick<T, Exclude<keyof T, K>>;
export type OmitControlledInputPropsFrom<T> = Omit<T, keyof IControlledInputProps>;

/** These props are used by Input components, such as TextInput */
export interface IValidationProps {
  touched?: boolean;
  validationMessage?: React.ReactNode;
  validationStatus?: IFieldValidationStatus;
  addValidator?: (validator: IValidator) => void;
  removeValidator?: (validator: IValidator) => void;
}

/** These props are used by Input components, such as TextInput */
export interface IFormInputProps extends Partial<IControlledInputProps> {
  validation?: IValidationProps;
  inputClassName?: string;
}

/** These props are used by FormFields such as FormikFormField and FormField */
export interface ICommonFormFieldProps {
  input: React.ComponentType<IFormInputProps>;
  layout?: React.ComponentType<IFieldLayoutProps>;
}

export interface IFormFieldApi {
  name(): string;
  label(): string;
  value(): any;
  touched(): boolean;
  validationMessage(): string;
  validationStatus(): IFieldValidationStatus;
}
