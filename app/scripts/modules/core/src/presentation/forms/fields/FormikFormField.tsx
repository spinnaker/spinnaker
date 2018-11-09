import * as React from 'react';
import { isString, isUndefined } from 'lodash';
import { Field, FastField, FieldProps, getIn } from 'formik';
import { WatchValue } from '../../WatchValue';

import { ICommonFormFieldProps, IFieldLayoutPropsWithoutInput, IValidationProps } from '../interface';
import { StandardFieldLayout } from '../layouts';
import { composeValidators, Validator, Validation } from '../Validation';
import { renderContent } from './renderContent';

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
  validate?: Validator | Validator[];
  /** A callback that is invoked whenever the field value changes */
  onChange?: (value: T, prevValue: T) => void;
}

export interface IFormikFormFieldState {
  internalValidators: Validator[];
}

export type IFormikFormFieldProps<T> = IFormikFieldProps<T> & ICommonFormFieldProps & IFieldLayoutPropsWithoutInput;

export class FormikFormField<T = any> extends React.Component<IFormikFormFieldProps<T>, IFormikFormFieldState> {
  public static defaultProps: Partial<IFormikFormFieldProps<any>> = {
    layout: StandardFieldLayout,
    fastField: true,
  };

  public state: IFormikFormFieldState = {
    internalValidators: [],
  };

  private addValidator = (internalValidator: Validator) => {
    this.setState(prevState => ({
      internalValidators: prevState.internalValidators.concat(internalValidator),
    }));
  };

  private removeValidator = (internalValidator: Validator) => {
    this.setState(prevState => ({
      internalValidators: prevState.internalValidators.filter(x => x !== internalValidator),
    }));
  };

  public render() {
    const { internalValidators } = this.state;
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
        addValidator: this.addValidator,
        removeValidator: this.removeValidator,
      };

      const inputElement = renderContent(input, { field, validation: validationProps });

      return (
        <WatchValue onChange={onChange} value={field.value}>
          {renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement })}
        </WatchValue>
      );
    };

    const validator = createFieldValidator(label, required, [].concat(validate).concat(internalValidators));

    if (this.props.fastField) {
      return <FastField name={name} validate={validator} render={render} />;
    }

    return <Field name={name} validate={validator} render={render} />;
  }
}

/** Returns a Validator composed of all the `validate` functions (and `isRequired` if `required` is truthy) */
export function createFieldValidator<T>(
  label: IFormikFormFieldProps<T>['label'],
  required: boolean,
  validate: Validator[],
): Validator {
  const validator = composeValidators([!!required && Validation.isRequired()].concat(validate));

  if (!validator) {
    return null;
  }

  const labelString = isString(label) ? label : undefined;
  return (value: any) => validator(value, labelString);
}
