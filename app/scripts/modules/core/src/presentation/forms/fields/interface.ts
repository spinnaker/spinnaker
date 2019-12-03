import React from 'react';

import { IFormInputProps, IFormInputValidation } from '../inputs';
import { ILayoutProps } from '../layouts';
import { IValidator } from '../validation';

/** These props are used by FormFields such as FormikFormField and FormField */
export interface ICommonFormFieldProps {
  /** The Form Input component (or render function) */
  input: React.ComponentType<IFormInputProps>;

  /**
   * An optional layout component (or render function).
   * If none is provided, the default layout (from context) will be used
   */
  layout?: React.ComponentType<ILayoutProps>;

  /** An inline validator function or functions */
  validate?: IValidator | IValidator[];

  /**
   * A string (or React Node) containing an explicit validation message for the Form Field.
   *
   * This string can be constructed using the helper methods from core/presentation i.e.:
   * errorMessage('Something went wrong!')
   *
   * Alternatively, a react node can be provided.
   */
  validationMessage?: IFormInputValidation['messageNode'];

  /** An explicit 'touched' status for the Form Field */
  touched?: IFormInputValidation['touched'];

  // The following props are a subset of layout props that are accepted by a FormField or FormikFormField

  /** A boolean marking this field as required */
  required?: ILayoutProps['required'];

  /** A string (or ReactNode) containing the field's label */
  label?: ILayoutProps['label'];

  /** A ReactNode containing the field's help component */
  help?: ILayoutProps['help'];

  /** A ReactNode containing the field's actions, such as 'delete item' */
  actions?: ILayoutProps['actions'];
}
