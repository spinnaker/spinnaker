import * as React from 'react';
import { Field, FieldProps, getIn } from 'formik';
import { isUndefined } from 'lodash';

import { ICommonFormFieldProps, IFieldLayoutPropsWithoutInput, IValidationProps } from '../interface';
import { StandardFieldLayout } from '../layouts';
import { renderContent } from './renderContent';
import { Validation, ValidationFunction } from '../Validation';

export interface IFormikFieldProps {
  name: string;
  validate?: ValidationFunction | ValidationFunction[];
}

export type IFormikFormFieldProps = IFormikFieldProps & ICommonFormFieldProps & IFieldLayoutPropsWithoutInput;

export class FormikFormField extends React.Component<IFormikFormFieldProps> {
  public static defaultProps: Partial<IFormikFormFieldProps> = {
    layout: StandardFieldLayout,
  };

  /** Returns validation function composed of all the `validate` functions (and `isRequired` if `required` is truthy) */
  private composedValidation(
    label: IFormikFormFieldProps['label'],
    required: boolean,
    validate: IFormikFieldProps['validate'],
  ): ValidationFunction {
    const labelStr = typeof label === 'string' ? label : 'This Field';
    const requiredFn = !!required && Validation.isRequired(`${labelStr} is required`);
    const validationFns = [requiredFn].concat(validate).filter(x => !!x);

    return validationFns.length ? Validation.compose(...validationFns) : null;
  }

  public render() {
    const { input, layout, name, validate } = this.props; // ICommonFieldProps & name & validate
    const { label, help, required, actions } = this.props; // IFieldLayoutPropsWithoutInput
    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };
    const { touched, validationMessage, validationStatus } = this.props; // IValidationProps

    return (
      <Field
        name={name}
        validate={this.composedValidation(label, required, validate)}
        render={(props: FieldProps<any>) => {
          const { field, form } = props;

          const formikError = getIn(form.errors, name);
          const message = !isUndefined(validationMessage) ? validationMessage : formikError;
          const status = !isUndefined(validationStatus) ? validationStatus : formikError ? 'error' : null;
          const isTouched = !isUndefined(touched) ? touched : getIn(form.touched, name);

          const validationProps: IValidationProps = {
            validationMessage: message,
            validationStatus: status,
            touched: isTouched,
          };

          const inputElement = renderContent(input, { field, validation: validationProps });
          return renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement });
        }}
      />
    );
  }
}
