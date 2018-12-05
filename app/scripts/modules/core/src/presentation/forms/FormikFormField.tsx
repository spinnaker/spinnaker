import * as React from 'react';
import { isString, isUndefined } from 'lodash';
import { Field, FastField, FieldProps, getIn, connect, FormikContext } from 'formik';

import {
  ICommonFormFieldProps,
  IFieldLayoutPropsWithoutInput,
  IFieldValidationStatus,
  IFormFieldApi,
  IValidationProps,
} from './interface';
import { WatchValue } from '../WatchValue';
import { StandardFieldLayout } from './layouts/index';
import { composeValidators, Validator, Validation } from './Validation';
import { renderContent } from './fields/renderContent';

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
  fastField?: boolean;
  /** Inline validation function or functions */
  validate?: Validator | Validator[];
  /** A callback that is invoked whenever the field value changes */
  onChange?: (value: T, prevValue: T) => void;
}

export interface IFormikFormFieldImplState {
  internalValidators: Validator[];
}

export type IFormikFormFieldProps<T> = IFormikFieldProps<T> & ICommonFormFieldProps & IFieldLayoutPropsWithoutInput;
type IFormikFormFieldImplProps<T> = IFormikFormFieldProps<T> & { formik: FormikContext<T> };

const ifString = (val: any): string => (isString(val) ? val : undefined);

export class FormikFormFieldImpl<T = any>
  extends React.Component<IFormikFormFieldImplProps<T>, IFormikFormFieldImplState>
  implements IFormFieldApi {
  public static defaultProps: Partial<IFormikFormFieldProps<any>> = {
    layout: StandardFieldLayout,
    fastField: true,
  };

  public state: IFormikFormFieldImplState = {
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

  public name = () => this.props.name;

  public label = () => ifString(this.props.label);

  public value = () => getIn(this.props.formik.values, this.props.name);

  public touched = () => {
    const { formik, name, touched } = this.props;
    return !isUndefined(touched) ? touched : getIn(formik.values, name);
  };

  public validationMessage = () => {
    const { name, formik, validationMessage } = this.props;
    return ifString(validationMessage) || getIn(formik.errors, name);
  };

  public validationStatus = () => {
    return (this.props.validationStatus || (this.validationMessage() ? 'error' : null)) as IFieldValidationStatus;
  };

  public render() {
    const { internalValidators } = this.state;
    const { name, validate, onChange } = this.props; // IFormikFieldProps
    const { input, layout } = this.props; // ICommonFieldProps
    const { label, help, required, actions } = this.props; // IFieldLayoutPropsWithoutInput

    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };

    const renderField = (props: FieldProps<any>) => {
      const { field } = props;

      const validationProps: IValidationProps = {
        touched: this.touched(),
        validationMessage: this.validationMessage(),
        validationStatus: this.validationStatus(),
        addValidator: this.addValidator,
        removeValidator: this.removeValidator,
      };

      const inputElement = renderContent(input, { ...field, validation: validationProps });

      return (
        <WatchValue onChange={onChange} value={field.value}>
          {renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement })}
        </WatchValue>
      );
    };

    const validator = createFieldValidator(label, required, [].concat(validate).concat(internalValidators));

    if (this.props.fastField) {
      return <FastField name={name} validate={validator} render={renderField} />;
    }

    return <Field name={name} validate={validator} render={renderField} />;
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

export const FormikFormField = connect(FormikFormFieldImpl);
