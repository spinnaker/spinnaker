import * as React from 'react';
import { FormikProps } from 'formik';

export interface IFieldLayoutPropsWithoutInput extends IValidationProps {
  label?: string | JSX.Element;
  help?: JSX.Element;
  required?: boolean;
  actions?: JSX.Element;
}

export interface IFieldLayoutProps extends IFieldLayoutPropsWithoutInput {
  input: JSX.Element;
}

export interface IValidationProps {
  error?: string | JSX.Element;
  touched?: boolean;
  warning?: string | JSX.Element;
  preview?: string | JSX.Element;
}

export interface IFieldProps<T = string> extends IFieldLayoutPropsWithoutInput, IValidationProps {
  value: T;
  onChange: (value: T) => void;
  FieldLayout?: React.ComponentType<IFieldLayoutProps>;
}

export interface IFormikFieldProps extends IFieldLayoutPropsWithoutInput {
  name: string;
  formik: FormikProps<any>;
  FieldLayout?: React.ComponentType<IFieldLayoutProps>;
}

export type Omit<T, K> = Pick<T, Exclude<keyof T, K>>;
// Exclude props from HTML input that collide with FormField props
export type PartialInputProps = Omit<React.HTMLProps<HTMLInputElement>, 'label' | 'onChange' | 'value'>;
