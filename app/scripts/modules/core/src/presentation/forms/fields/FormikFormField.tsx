import * as React from 'react';
import { isUndefined } from 'lodash';
import { FastField, Field, FieldProps, getIn } from 'formik';

import { ICommonFormFieldProps, IFieldLayoutPropsWithoutInput, IValidationProps } from '../interface';
import { StandardFieldLayout } from '../layouts';
import { Validation, ValidationFunction } from '../Validation';
import { renderContent } from './renderContent';
import { WatchValue } from '../../WatchValue';

export interface IFormikFieldProps<T> {
  /**
   * The name/path to the field in the Formik form.
   * Accepts lodash paths; see: https://lodash.com/docs/#get
   */
  name: string;
  /**
   * Toggles between `Field` (false) and `FastField` (true)
   * Defaults to `FastField` (true)
   *
   * Use `fastField={false}` if the field depends on other fields.
   * See: https://jaredpalmer.com/formik/docs/api/fastfield#when-to-use-fastfield
   */
  fastField: boolean;
  /** Inline validation function or functions */
  validate?: ValidationFunction | ValidationFunction[];
  /** A callback that is invoked whenever the field value changes */
  onChange?: (value: T, prevValue: T) => void;
}

export type IFormikFormFieldProps<T> = IFormikFieldProps<T> & ICommonFormFieldProps & IFieldLayoutPropsWithoutInput;

export class FormikFormField<T = any> extends React.Component<IFormikFormFieldProps<T>> {
  public static defaultProps: Partial<IFormikFormFieldProps<any>> = {
    layout: StandardFieldLayout,
    fastField: true,
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

    const render = (props: FieldProps<any>) => {
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
    };

    const validator = this.composedValidation(label, required, validate);

    if (this.props.fastField) {
      return <FastField name={name} validate={validator} render={render} />;
    }

    return <Field name={name} validate={validator} render={render} />;
  }
}
