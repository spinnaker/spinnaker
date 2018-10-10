import * as React from 'react';
import { FieldProps } from 'formik';

/** These props are used by FormField and FormikFormField components */
export interface IFieldLayoutPropsWithoutInput extends IValidationProps {
  required?: boolean;
  label?: string | JSX.Element;
  help?: string | JSX.Element;
  actions?: string | JSX.Element;
}

/** These props are used by FieldLayout components, such as StandardFieldLayout */
export interface IFieldLayoutProps extends IFieldLayoutPropsWithoutInput {
  input: string | JSX.Element;
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
  error?: string | JSX.Element;
  touched?: boolean;
  warning?: string | JSX.Element; // TODO: remove this prop
  preview?: string | JSX.Element; // TODO: remove this prop
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
