import * as React from 'react';

import { firstDefined, noop } from 'core/utils';

import { IControlledInputProps, IFormInputProps, IFormInputValidation } from '../inputs';
import { ILayoutProps, LayoutContext } from '../layouts';
import { IValidator, useValidationData } from '../validation';

import { ICommonFormFieldProps } from './interface';
import { createFieldValidator } from './FormikFormField';
import { renderContent } from './renderContent';

import '../forms.less';

export type IFormFieldProps = ICommonFormFieldProps & Partial<IControlledInputProps>;

const { useState, useCallback, useContext, useMemo } = React;

export function FormField(props: IFormFieldProps): JSX.Element {
  const { input, layout, label, help, required, actions } = props;
  const { value } = props;

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

  const FieldLayoutFromContext = useContext(LayoutContext);
  const inputRenderPropOrNode = firstDefined(input, noop);
  const layoutFromContext = (fieldLayoutProps: ILayoutProps) => <FieldLayoutFromContext {...fieldLayoutProps} />;
  const layoutRenderPropOrNode = firstDefined(layout, layoutFromContext);

  const [hasBlurred, setHasBlurred] = useState(false);
  const touched = firstDefined(props.touched, hasBlurred);
  const validatorResult = useMemo(() => fieldValidator(value), [fieldValidator, value]);
  const validationMessage = firstDefined(props.validationMessage, validatorResult ? validatorResult : undefined);
  const validationData = useValidationData(validationMessage, touched);

  const validation: IFormInputValidation = {
    touched,
    addValidator,
    removeValidator,
    ...validationData,
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
  const inputProps: IFormInputProps = { ...controlledInputProps, validation };
  const inputElement = renderContent(inputRenderPropOrNode, inputProps);

  // Render the layout passing the rendered input in
  const layoutProps: ILayoutProps = { label, help, required, actions, validation, input: inputElement };
  return <>{renderContent(layoutRenderPropOrNode, layoutProps)}</>;
}
