import * as React from 'react';
import { isNil, isString } from 'lodash';
import { Field, FastField, FieldProps, getIn, FormikContext, FormikConsumer } from 'formik';

import { ICommonFormFieldProps, IFieldLayoutPropsWithoutInput, IValidationProps } from './interface';
import { WatchValue } from '../WatchValue';
import { LayoutContext } from './layouts/index';
import { composeValidators, IValidator, Validators } from './validation';
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
  validate?: IValidator | IValidator[];
  /** A callback that is invoked whenever the field value changes */
  onChange?: (value: T, prevValue: T) => void;
}

export type IFormikFormFieldProps<T> = IFormikFieldProps<T> & ICommonFormFieldProps & IFieldLayoutPropsWithoutInput;
type IFormikFormFieldImplProps<T> = IFormikFormFieldProps<T> & { formik: FormikContext<T> };

function firstDefined<T>(...values: T[]): T {
  return values.find(val => !isNil(val));
}

const { useCallback, useContext, useState } = React;

function FormikFormFieldImpl<T = any>(props: IFormikFormFieldImplProps<T>) {
  const { formik } = props;
  const { name, validate, onChange } = props; // IFormikFieldProps
  const { input, layout } = props; // ICommonFieldProps
  const { label, help, required, actions } = props; // IFieldLayoutPropsWithoutInput
  const {
    validationMessage: messageProp,
    validationStatus: statusProp,
    touched: touchedProp,
    fastField: fastFieldProp,
  } = props;

  const validationMessage = firstDefined(messageProp, getIn(formik.errors, props.name) as string);
  const validationStatus = firstDefined(statusProp, validationMessage ? 'error' : null);
  const touched = firstDefined(touchedProp, getIn(formik.touched, name) as boolean);
  const fastField = firstDefined(fastFieldProp, true);

  const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };
  const fieldLayoutFromContext = useContext(LayoutContext);

  const [internalValidators, setInternalValidators] = useState([]);
  const addValidator = useCallback((v: IValidator) => setInternalValidators(list => list.concat(v)), []);
  const removeValidator = useCallback((v: IValidator) => setInternalValidators(list => list.filter(x => x !== v)), []);

  const renderField = ({ field }: FieldProps<any>) => {
    const validationProps: IValidationProps = {
      touched,
      validationMessage,
      validationStatus,
      addValidator,
      removeValidator,
    };

    const inputElement = renderContent(input, { ...field, validation: validationProps });

    return (
      <WatchValue onChange={onChange} value={field.value}>
        {renderContent(layout || fieldLayoutFromContext, {
          ...fieldLayoutPropsWithoutInput,
          ...validationProps,
          input: inputElement,
        })}
      </WatchValue>
    );
  };

  const validator = createFieldValidator(label, required, [].concat(validate).concat(internalValidators));

  if (fastField) {
    return <FastField name={name} validate={validator} render={renderField} />;
  }

  return <Field name={name} validate={validator} render={renderField} />;
}

/** Returns a Validator composed of all the `validate` functions (and `isRequired` if `required` is truthy) */
export function createFieldValidator<T>(
  label: IFormikFormFieldProps<T>['label'],
  required: boolean,
  validate: IValidator[],
): IValidator {
  const validators = [!!required && Validators.isRequired()].concat(validate);
  const validator = composeValidators(validators);

  if (!validator) {
    return null;
  }

  const labelString = isString(label) ? label : undefined;
  return (value: any) => validator(value, labelString);
}

export function FormikFormField<T = any>(props: IFormikFormFieldProps<T>) {
  return <FormikConsumer>{formik => <FormikFormFieldImpl {...props} formik={formik} />}</FormikConsumer>;
}
