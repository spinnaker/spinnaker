import { defaults, isFunction, isNil, isString } from 'lodash';
import React from 'react';

import { IMapPair, MapPair } from './MapPair';
import { IPipeline } from '../../domain';
import { createFakeReactSyntheticEvent, IFormInputProps, IValidator, IValidatorResult } from '../../presentation';

import './MapEditor.less';

export interface IMapEditorInputProps extends IFormInputProps {
  addButtonLabel?: string;
  allowEmptyValues?: boolean;
  hiddenKeys?: string[];
  keyLabel?: string;
  label?: string;
  labelsLeft?: boolean;
  valueLabel?: string;
  valueCanContainSpel?: boolean;
  pipeline?: IPipeline;
}

export interface IMapEditorModel {
  [key: string]: string;
}

const duplicateKeyPattern = /^__MapEditorDuplicateKey__\d+__/;

// Convert the controlled component value (an object) to a list of IMapPairs
function objectToTuples(model: IMapEditorModel, errors: IMapEditorModel): IMapPair[] {
  model = model || {};
  errors = errors || {};
  return Object.keys(model).map((key) => {
    const keyWithoutMagicString = key.split(duplicateKeyPattern).pop();
    return { key: keyWithoutMagicString, value: model[key], error: errors[key] };
  });
}

// Convert a list of IMapPairs to an object with keys and values
// Prepend any duplicate keys with a magic string
function tuplesToObject(pairs: IMapPair[]): IMapEditorModel {
  return pairs.reduce((acc, pair, idx) => {
    // Cannot have duplicate keys in an object, so prepend a magic string to the key
    const key = isNil(acc[pair.key]) ? pair.key : `__MapEditorDuplicateKey__${idx}__${pair.key}`;
    return { ...acc, [key]: pair.value };
  }, {} as IMapEditorModel);
}

function mapEditorValidator(options?: { allowEmptyValues: boolean }): IValidator {
  const opts = defaults({}, options, { allowEmptyValues: false });
  return function (values: IMapEditorModel): IValidatorResult {
    const errors = Object.keys(values || {}).reduce((acc, key) => {
      if (!key) {
        return { ...acc, [key]: 'Empty key' };
      } else if (duplicateKeyPattern.exec(key)) {
        return { ...acc, [key]: 'Duplicate key' };
      } else if (!opts.allowEmptyValues && !values[key]) {
        return { ...acc, [key]: 'Empty value' };
      }
      return acc;
    }, {}) as any;

    return Object.keys(errors).length ? errors : null;
  };
}

export function MapEditorInput({
  addButtonLabel = 'Add Field',
  allowEmptyValues = false,
  hiddenKeys = [],
  keyLabel = 'Key',
  label,
  labelsLeft = false,
  name,
  onChange,
  value,
  valueLabel = 'Value',
  valueCanContainSpel = false,
  validation,
  pipeline,
}: IMapEditorInputProps) {
  const rowProps = { keyLabel, valueLabel, labelsLeft };
  const validator = React.useRef(mapEditorValidator({ allowEmptyValues }));

  const columnCount = labelsLeft ? 5 : 3;
  const tableClass = label ? '' : 'no-border-top';
  const isParameterized = isString(value);
  const backingModel = !isString(value) ? objectToTuples(value, (validation && validation.messageNode) || {}) : null;

  // Register/unregister validator, if a validation prop was supplied
  React.useEffect(() => {
    if (validation && isFunction(validation.addValidator)) {
      validation.addValidator((validator.current as any) as IValidator);
    }

    return () => {
      if (validation && isFunction(validation.removeValidator)) {
        validation.removeValidator((validator.current as any) as IValidator);
      }
    };
  }, []);

  const handleChanged = () => {
    onChange(createFakeReactSyntheticEvent({ name, value: tuplesToObject(backingModel) }));
  };

  const handlePairChanged = (newPair: IMapPair, index: number) => {
    backingModel[index] = newPair;
    handleChanged();
  };

  const handleDeletePair = (index: number) => {
    backingModel.splice(index, 1);
    handleChanged();
  };

  const handleAddPair = () => {
    backingModel.push({ key: '', value: '' });
    handleChanged();
  };

  return (
    <div className="MapEditor">
      {label && (
        <div className="sm-label-left">
          <b>{label}</b>
        </div>
      )}

      {isParameterized && <input className="form-control input-sm" value={value as string} />}
      {!isParameterized && (
        <table className={`table table-condensed packed tags ${tableClass}`}>
          <thead>
            {!labelsLeft && (
              <tr>
                <th>{keyLabel}</th>
                <th>{valueLabel}</th>
                <th />
              </tr>
            )}
          </thead>
          <tbody>
            {backingModel
              .filter((p) => !hiddenKeys.includes(p.key))
              .map((pair, index) => (
                <MapPair
                  key={index}
                  {...rowProps}
                  onChange={(x) => handlePairChanged(x, index)}
                  onDelete={() => handleDeletePair(index)}
                  pair={pair}
                  valueCanContainSpel={valueCanContainSpel}
                  pipeline={pipeline}
                />
              ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={columnCount}>
                <button type="button" className="btn btn-block btn-sm add-new" onClick={handleAddPair}>
                  <span className="glyphicon glyphicon-plus-sign" />
                  {addButtonLabel}
                </button>
              </td>
            </tr>
          </tfoot>
        </table>
      )}
    </div>
  );
}
