import { Field, FieldProps, getIn } from 'formik';
import { isUndefined } from 'lodash';
import * as React from 'react';

import { ICommonFormFieldProps, IFieldLayoutPropsWithoutInput, IValidationProps } from '../interface';
import { StandardFieldLayout } from '../layouts';
import { Validation, ValidationFunction } from '../Validation';
import { renderContent } from './renderContent';
import { WatchValue } from '../../WatchValue';

export interface IFormikFieldProps<T> {
  name: string;
  validate?: ValidationFunction | ValidationFunction[];
  onChange?: (value: T, prevValue: T) => void;
}

export type IFormikFormFieldProps<T> = IFormikFieldProps<T> & ICommonFormFieldProps & IFieldLayoutPropsWithoutInput;

export class FormikFormField<T = any> extends React.Component<IFormikFormFieldProps<T>> {
  public static defaultProps: Partial<IFormikFormFieldProps<any>> = {
    layout: StandardFieldLayout,
  };

  /** Returns validation function composed of all the `validate` functions (and `isRequired` if `required` is truthy) */
  private composedValidation(
    label: IFormikFormFieldProps<T>['label'],
    required: boolean,
    validate: IFormikFieldProps<T>['validate'],
  ): ValidationFunction {
    const labelStr = typeof label === 'string' ? label : 'This field';
    const requiredFn = !!required && Validation.isRequired(`${labelStr} is required`);
    const validationFns = [requiredFn].concat(validate).filter(x => !!x);

    return validationFns.length ? Validation.compose(...validationFns) : null;
  }

  public render() {
    const { name, validate, onChange } = this.props; // IFormikFieldProps
    const { input, layout } = this.props; // ICommonFieldProps
    const { label, help, required, actions } = this.props; // IFieldLayoutPropsWithoutInput
    const { touched, validationMessage, validationStatus } = this.props; // IValidationProps

    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };

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

          return (
            <WatchValue onChange={onChange} value={field.value}>
              {renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement })}
            </WatchValue>
          );
        }}
      />
    );
  }
}
