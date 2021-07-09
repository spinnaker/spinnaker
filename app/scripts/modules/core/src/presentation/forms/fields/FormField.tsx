import React from 'react';

import { createFieldValidator } from './FormikFormField';
import { IControlledInputProps, IFormInputProps, IFormInputValidation } from '../inputs';
import { ICommonFormFieldProps } from './interface';
import { ILayoutProps, LayoutContext } from '../layouts';
import { renderContent } from './renderContent';
import { firstDefined, noop } from '../../../utils';
import { IValidator, useValidationData } from '../validation';

import '../forms.less';

export type IFormFieldProps = ICommonFormFieldProps & Partial<IControlledInputProps>;

const { useState, useCallback, useContext, useMemo } = React;

export function FormField(props: IFormFieldProps): JSX.Element {
  const { input, layout, label, help, required, actions } = props;
  const { value } = props;

  // Internal validators are defined by an Input component
  const [internalValidators, setInternalValidators] = useState([]);
  const [revalidateRequestId, setRevalidateRequestId] = useState(0);
  const addValidator = useCallback((v: IValidator) => setInternalValidators((list) => list.concat(v)), []);
  const removeValidator = useCallback(
    (v: IValidator) => setInternalValidators((list) => list.filter((x) => x !== v)),
    [],
  );

  // Capture props.validate when the component initializes (to allow for inline arrow functions)
  const validate = useMemo(() => props.validate, []);

  const fieldValidator = useMemo(
    () => createFieldValidator(label, required, [].concat(validate || noop).concat(internalValidators)),
    [label, required, validate, internalValidators.length],
  );

  const FieldLayoutFromContext = useContext(LayoutContext);
  const inputRenderPropOrNode = firstDefined(input, noop);
  const layoutFromContext = (fieldLayoutProps: ILayoutProps) => <FieldLayoutFromContext {...fieldLayoutProps} />;
  const layoutRenderPropOrNode = firstDefined(layout, layoutFromContext);

  const [hasBlurred, setHasBlurred] = useState(false);
  const touched = firstDefined(props.touched, hasBlurred);

  // When called, this bumps a revalidateRequestId which in causes validatorResult to be updated
  const revalidate = () => setRevalidateRequestId((prev) => prev + 1);
  const validatorResult = useMemo(() => fieldValidator(value), [fieldValidator, value, revalidateRequestId]);
  const validationMessage = firstDefined(props.validationMessage, validatorResult ? validatorResult : undefined);
  const validationData = useValidationData(validationMessage, touched);

  const validation: IFormInputValidation = {
    touched,
    addValidator,
    removeValidator,
    revalidate,
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
