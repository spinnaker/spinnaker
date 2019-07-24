import * as React from 'react';
import Select, { Option, ReactSelectProps } from 'react-select';
import VirtualizedSelect from 'react-virtualized-select';

import { noop } from 'core/utils';

import { StringsAsOptions } from './StringsAsOptions';
import { TetheredSelect } from '../../TetheredSelect';
import { createFakeReactSyntheticEvent, isStringArray, orEmptyString } from './utils';
import { IFormInputProps, OmitControlledInputPropsFrom } from '../interface';

export interface IReactSelectInputProps extends IFormInputProps, OmitControlledInputPropsFrom<ReactSelectProps> {
  stringOptions?: string[];
  mode?: 'TETHERED' | 'VIRTUALIZED' | 'PLAIN';
}

// TODO: use standard css classes (from style guide?)
// Currently the form-control class is needed for ng-invalid, but messes up the rendering of react-select
export const reactSelectValidationErrorStyle = {
  borderColor: 'var(--color-danger)',
  WebkitBoxShadow: 'inset 0 1px 1px rgba(0, 0, 0, 0.075)',
  boxShadow: 'inset 0 1px 1px rgba(0, 0, 0, 0.075)',
};

/**
 * Given a IControlledInputProps "field" (i.e., from Formik), returns an onChange handler
 * somewhat compatible with the controlled input pattern
 */
export const reactSelectOnChangeAdapter = (name: string, onChange: IReactSelectInputProps['onChange']) => {
  return (selectedOption: Option) => {
    const target = { name, value: selectedOption ? selectedOption.value : null };
    const event = createFakeReactSyntheticEvent(target);
    return (onChange || noop)(event);
  };
};

/**
 * Given a IControlledInputProps "field" (i.e., from Formik), returns an onBlur handler
 * somewhat compatible with the controlled input pattern
 */
export const reactSelectOnBlurAdapter = (name: string, value: any, onBlur: IReactSelectInputProps['onBlur']) => {
  return () => {
    const target = { name, value };
    const event = createFakeReactSyntheticEvent(target);
    return (onBlur || noop)(event);
  };
};

/**
 * A react-select Input
 *
 * This input supports error validation state rendering. It adapts the onChange event to a controlled input event.
 *
 * This component does not attempt to support async loading
 */
export class ReactSelectInput extends React.Component<IReactSelectInputProps> {
  public static defaultProps: Partial<IReactSelectInputProps> = {
    mode: 'TETHERED',
  };

  public render() {
    const {
      name,
      onChange,
      onBlur,
      value,
      mode,
      validation,
      stringOptions,
      options: optionOptions,
      inputClassName,
      ...otherProps
    } = this.props;

    const className = orEmptyString(inputClassName);
    const style = (validation || {}).validationStatus === 'error' ? reactSelectValidationErrorStyle : {};
    const fieldProps = {
      name,
      value: orEmptyString(value),
      onBlur: reactSelectOnBlurAdapter(name, value, onBlur),
      onChange: reactSelectOnChangeAdapter(name, onChange),
    };

    const SelectElement = ({ options }: { options: IReactSelectInputProps['options'] }) =>
      mode === 'TETHERED' ? (
        <TetheredSelect className={className} style={style} options={options} {...fieldProps} {...otherProps} />
      ) : mode === 'VIRTUALIZED' ? (
        <VirtualizedSelect
          className={className}
          style={style}
          options={options}
          {...fieldProps}
          {...otherProps}
          optionRenderer={null}
        />
      ) : (
        <Select className={className} style={style} options={options} {...fieldProps} {...otherProps} />
      );

    if (isStringArray(stringOptions)) {
      return (
        <StringsAsOptions strings={stringOptions}>{options => <SelectElement options={options} />}</StringsAsOptions>
      );
    } else {
      return <SelectElement options={optionOptions} />;
    }
  }
}
