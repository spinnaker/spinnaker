import { FieldProps } from 'formik';
import React from 'react';

import { IValidationCategory, IValidator } from '../validation';

/**
 * These props are used by controlled components, such as <input> or Input components like TextInput
 * Some of the typings reference the typings supplied by formik FieldProps
 */
export interface IControlledInputProps {
  value: FieldProps['field']['value'];

  onChange(e: React.ChangeEvent<any>): void;

  onBlur: FieldProps['field']['onBlur'];
  name: FieldProps['field']['name'];
}

export type Omit<T, K> = Pick<T, Exclude<keyof T, K>>;
export type OmitControlledInputPropsFrom<T> = Omit<T, keyof IControlledInputProps>;

/** These props are used by Input components, such as TextInput */
export interface IFormInputValidation {
  touched: boolean;
  hidden: boolean;
  category: IValidationCategory | undefined;
  messageNode: React.ReactNode | undefined;
  revalidate: () => void;
  addValidator: (validator: IValidator) => void;
  removeValidator: (validator: IValidator) => void;
}

/** These props are used by Form Input components, such as TextInput */
export interface IFormInputProps extends Partial<IControlledInputProps> {
  validation?: IFormInputValidation;
  inputClassName?: string;
}
