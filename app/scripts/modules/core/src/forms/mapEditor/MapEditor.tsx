import * as React from 'react';
import { isString, isFunction } from 'lodash';

import { IPipeline } from 'core/domain';
import { IValidationProps, IValidator } from 'core/presentation';

import { MapEditorInput, IMapEditorModel } from './MapEditorInput';

export interface IMapEditorProps {
  addButtonLabel?: string;
  allowEmpty?: boolean;
  hiddenKeys?: string[];
  keyLabel?: string;
  label?: string;
  labelsLeft?: boolean;
  model: string | IMapEditorModel;
  valueLabel?: string;
  onChange: (model: string | IMapEditorModel, error: boolean) => void;
  valueCanContainSpel?: boolean;
  pipeline?: IPipeline;
}
function doValidation(validator: IValidator, value: string | IMapEditorModel): IMapEditorModel {
  if (isString(value)) {
    return null;
  }

  const newErrors = isFunction(validator) ? validator(value) : null;
  return (newErrors as any) as IMapEditorModel;
}

// A component that adapts the MapEditorInput (a controlled component) to the previous API of MapEditor
// Handles validation and feeds it back into the MapEditorInput
export function MapEditor(mapEditorProps: IMapEditorProps) {
  const { onChange, model: initialModel, ...props } = mapEditorProps;
  const [model, setModel] = React.useState<string | IMapEditorModel>(initialModel);
  const [validator, setValidator] = React.useState<IValidator>();
  const [errors, setErrors] = React.useState<IMapEditorModel>();

  React.useEffect(() => {
    const newErrors = doValidation(validator, model);
    const hasError = !!Object.keys(newErrors || {}).length;
    setErrors(newErrors);
    onChange(model, hasError);
  }, [validator, model]);

  const validation: IValidationProps = {
    touched: true,
    // Use setValidator(oldstate => newstate) overload
    // Otherwise, react calls the validator function internally and stores the returned errors object
    // https://reactjs.org/docs/hooks-reference.html#functional-updates
    addValidator: newValidator => setValidator(() => newValidator),
    removeValidator: () => setValidator(null),
  };

  return (
    <MapEditorInput
      {...props}
      errors={errors}
      value={model}
      onChange={e => setModel(e.target.value)}
      validation={validation}
    />
  );
}
