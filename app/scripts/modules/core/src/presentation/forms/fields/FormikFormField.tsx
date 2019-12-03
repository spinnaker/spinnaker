import React from 'react';
import { isString } from 'lodash';
import { Field, FastField, FieldProps, getIn, FormikContext, FormikConsumer } from 'formik';

import { noop, firstDefined } from 'core/utils';

import { WatchValue } from '../../WatchValue';
import { composeValidators, IValidator, useValidationData, Validators } from '../validation';
import { ICommonFormFieldProps, renderContent } from './index';
import { IFormInputValidation } from '../inputs';
import { LayoutContext, ILayoutProps } from '../layouts';

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

  /** A callback that is invoked whenever the field value changes */
  onChange?: (value: T, prevValue: T) => void;
}

export type IFormikFormFieldProps<T> = ICommonFormFieldProps & IFormikFieldProps<T>;
type IFormikFormFieldImplProps<T> = IFormikFormFieldProps<T> & { formik: FormikContext<T> };

const { useCallback, useContext, useState } = React;

function FormikFormFieldImpl<T = any>(props: IFormikFormFieldImplProps<T>) {
  const { formik } = props;
  const { name, onChange, fastField: fastFieldProp } = props;
  const { input, layout, label, help, required, actions, validate, validationMessage, touched: touchedProp } = props;

  const FieldLayoutFromContext = useContext(LayoutContext);

  const formikTouched = getIn(formik.touched, name);
  const formikError = getIn(formik.errors, props.name);
  const fastField = firstDefined(fastFieldProp, true);
  const touched = firstDefined(touchedProp, formikTouched as boolean);

  const message = firstDefined(validationMessage, formikError as string);
  const { hidden, category, messageNode } = useValidationData(message, touched);

  const [internalValidators, setInternalValidators] = useState([]);
  const addValidator = useCallback((v: IValidator) => setInternalValidators(list => list.concat(v)), []);
  const removeValidator = useCallback((v: IValidator) => setInternalValidators(list => list.filter(x => x !== v)), []);

  function revalidate() {
    formik.validateForm();
  }

  React.useEffect(() => revalidate(), [internalValidators]);

  const validation: IFormInputValidation = {
    touched,
    revalidate,
    addValidator,
    removeValidator,
    hidden,
    category,
    messageNode,
  };

  const renderField = ({ field }: FieldProps<any>) => {
    const inputRenderPropOrNode = firstDefined(input, noop);
    const layoutFromContext = (fieldLayoutProps: ILayoutProps) => <FieldLayoutFromContext {...fieldLayoutProps} />;
    const layoutRenderPropOrNode = firstDefined(layout, layoutFromContext);
    const inputElement = renderContent(inputRenderPropOrNode, { ...field, validation });

    const layoutProps: ILayoutProps = { label, help, required, actions, input: inputElement, validation };

    return (
      <WatchValue onChange={onChange} value={field.value}>
        {renderContent(layoutRenderPropOrNode, layoutProps)}
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
