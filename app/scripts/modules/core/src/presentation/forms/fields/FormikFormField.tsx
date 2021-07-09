import { FastField, Field, FieldProps, FormikConsumer, FormikContext } from 'formik';
import { isString, toPath } from 'lodash';
import React from 'react';

import { WatchValue } from '../../WatchValue';
import { useMountStatusRef } from '../../hooks/useMountStatusRef.hook';
import { ICommonFormFieldProps, renderContent } from './index';
import { IFormInputValidation } from '../inputs';
import { ILayoutProps, LayoutContext } from '../layouts';
import { FormikSpelContext, SimpleSpelInput, SpelAwareInputMode, SpelService, SpelToggle } from '../../spel';
import { firstDefined } from '../../../utils';
import { composeValidators, IValidator, useValidationData, Validators } from '../validation';

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
   * Use `fastField={true}` if the field doesn't depend on any other fields or external (i.e., async) data
   * See: https://jaredpalmer.com/formik/docs/api/fastfield#when-to-use-fastfield
   */
  fastField?: boolean;

  /** A callback that is invoked whenever the field value changes */
  onChange?: (value: T, prevValue: T) => void;
  /** Configures SpEL-awareness of the input.
   * Overrides configuration set on FormikSpelContext.
   */
  spelAware?: boolean;
}

export type IFormikFormFieldProps<T> = ICommonFormFieldProps & IFormikFieldProps<T>;
type IFormikFormFieldImplProps<T> = IFormikFormFieldProps<T> & { formik: FormikContext<T> };

const { useCallback, useContext, useState } = React;

const revalidateMap = new Map();
// If many formfields request a revalidate at the same time, coalesce the requests and revalidate once on the next tick
function coalescedRevalidate(formik: FormikContext<any>) {
  if (!revalidateMap.has(formik)) {
    revalidateMap.set(formik, true);
    setTimeout(() => {
      revalidateMap.delete(formik);
      return formik.validateForm();
    });
  }
}

/**
 * Deeply get a value from an object via its path.
 */
function getIn(obj: any, key: string, defaultValue: any = undefined) {
  let p = 0;
  const path = toPath(key);
  while (obj && p < path.length) {
    obj = typeof obj == 'string' ? undefined : obj[path[p++]];
  }
  return obj === undefined ? defaultValue : obj;
}

function FormikFormFieldImpl<T = any>(props: IFormikFormFieldImplProps<T>) {
  const { formik } = props;
  const { name, onChange, fastField: fastFieldProp } = props;
  const { input, layout, label, help, required, actions, validate, validationMessage, touched: touchedProp } = props;

  const FieldLayoutFromContext = useContext(LayoutContext);
  const SpelAwareFromContext = useContext(FormikSpelContext);

  const formikTouched = getIn(formik.touched, name);
  const formikError = getIn(formik.errors, props.name);
  const fastField = firstDefined(fastFieldProp, false);
  const touched = firstDefined(touchedProp, formikTouched as boolean);

  const message = firstDefined(validationMessage, formikError as string);
  const { hidden, category, messageNode } = useValidationData(message, touched);

  const [internalValidators, setInternalValidators] = useState([]);
  const addValidator = useCallback((v: IValidator) => setInternalValidators((list) => list.concat(v)), []);
  const removeValidator = useCallback(
    (v: IValidator) => setInternalValidators((list) => list.filter((x) => x !== v)),
    [],
  );

  const revalidate = () => coalescedRevalidate(formik);
  const mountStatus = useMountStatusRef();
  React.useEffect(() => {
    if (mountStatus.current === 'FIRST_RENDER' && internalValidators.length === 0) {
      return;
    }
    revalidate();
  }, [internalValidators]);

  const validation: IFormInputValidation = {
    touched,
    revalidate,
    addValidator,
    removeValidator,
    hidden,
    category,
    messageNode,
  };

  const freeformInputAllowed = firstDefined(props.spelAware, SpelAwareFromContext, false);

  const initialInputMode = React.useMemo(() => {
    const fieldValue = getIn(props.formik.values, name, '');
    return freeformInputAllowed && SpelService.includesSpel(fieldValue)
      ? SpelAwareInputMode.FREEFORM
      : SpelAwareInputMode.DEFAULT;
  }, []);

  const [inputMode, setInputMode] = React.useState(initialInputMode);

  const onSpelToggleClick = () => {
    formik.setFieldValue(name, null);
    setInputMode(inputMode === SpelAwareInputMode.DEFAULT ? SpelAwareInputMode.FREEFORM : SpelAwareInputMode.DEFAULT);
  };

  const renderField = ({ field }: FieldProps<any>) => {
    const inputOrSpel = inputMode === SpelAwareInputMode.DEFAULT ? input : SimpleSpelInput;
    const layoutFromContext = (fieldLayoutProps: ILayoutProps) => <FieldLayoutFromContext {...fieldLayoutProps} />;
    const layoutRenderPropOrNode = firstDefined(layout, layoutFromContext);
    const inputElement = renderContent(inputOrSpel, { ...field, validation });

    const composedActions = (
      <>
        {actions}
        {freeformInputAllowed && <SpelToggle inputMode={inputMode} onClick={onSpelToggleClick} />}
      </>
    );

    const layoutProps: ILayoutProps = {
      label,
      help,
      required,
      actions: composedActions,
      input: inputElement,
      validation,
    };

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
  return <FormikConsumer>{(formik) => <FormikFormFieldImpl {...props} formik={formik} />}</FormikConsumer>;
}
