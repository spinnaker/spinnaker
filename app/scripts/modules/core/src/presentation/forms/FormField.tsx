import * as React from 'react';
import { isNil } from 'lodash';

import { noop } from 'core/utils';

import { LayoutContext } from './layouts';
import { createFieldValidator } from './FormikFormField';
import { renderContent } from './fields/renderContent';
import { IValidator } from './validation';
import {
  ICommonFormFieldProps,
  IControlledInputProps,
  IFieldLayoutProps,
  IFieldLayoutPropsWithoutInput,
  IValidationProps,
} from './interface';

import './forms.less';

export interface IFormFieldValidationProps {
  validate?: IValidator | IValidator[];
}

export type IFormFieldProps = IFormFieldValidationProps &
  ICommonFormFieldProps &
  Partial<IControlledInputProps> &
  IFieldLayoutPropsWithoutInput &
  IValidationProps;

function firstDefined<T>(...values: T[]): T {
  return values.find(val => !isNil(val));
}

const { useState, useCallback, useContext, useMemo } = React;

export function FormField(props: IFormFieldProps) {
  const { input, layout } = props; // ICommonFormFieldProps
  const { label, help, required, actions } = props; // IFieldLayoutPropsWithoutInput
  const { validationMessage: messageProp, validationStatus: statusProp, touched: touchedProp } = props;
  const { value } = props;

  const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };

  // Internal validators are defined by an Input component
  const [internalValidators, setInternalValidators] = useState([]);
  const addValidator = useCallback((v: IValidator) => setInternalValidators(list => list.concat(v)), []);
  const removeValidator = useCallback((v: IValidator) => setInternalValidators(list => list.filter(x => x !== v)), []);

  // Capture props.validate when the component initializes (to allow for inline arrow functions)
  const validate = useMemo(() => props.validate, []);

  const fieldValidator = useMemo(
    () => createFieldValidator(label, required, [].concat(validate || noop).concat(internalValidators)),
    [label, required, validate],
  );

  const errorMessage = useMemo(() => fieldValidator(value), [fieldValidator, value]);

  const validationMessage = firstDefined(messageProp, errorMessage ? errorMessage : undefined);
  const validationStatus = firstDefined(statusProp, errorMessage ? 'error' : undefined);

  const [hasBlurred, setHasBlurred] = useState(false);

  const touched = firstDefined(touchedProp, hasBlurred);

  const FieldLayoutFromContext = useContext(LayoutContext);
  const inputRenderPropOrNode = firstDefined(input, noop);
  const layoutFromContext = (layoutProps: IFieldLayoutProps) => <FieldLayoutFromContext {...layoutProps} />;
  const layoutRenderPropOrNode = firstDefined(layout, layoutFromContext);

  const validationProps: IValidationProps = {
    touched,
    validationMessage,
    validationStatus,
    addValidator,
    removeValidator,
  };

  const controlledInputProps: IControlledInputProps = {
    value: props.value,
    name: props.name || '',
    onChange: props.onChange || noop,
    onBlur: (e: React.FocusEvent) => {
      setHasBlurred(true);
      props.onBlur && props.onBlur(e);
    },
  };

  // Render the input
  const inputElement = renderContent(inputRenderPropOrNode, { ...controlledInputProps, validation: validationProps });

  // Render the layout passing the rendered input in
  return (
    <>
      {renderContent(layoutRenderPropOrNode, {
        ...fieldLayoutPropsWithoutInput,
        ...validationProps,
        input: inputElement,
      })}
    </>
  );
}
