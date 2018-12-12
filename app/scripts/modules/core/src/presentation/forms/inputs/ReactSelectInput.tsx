import * as React from 'react';
import Select, { Option, ReactSelectProps } from 'react-select';

import { OmitControlledInputPropsFrom, StringsAsOptions, TetheredSelect } from 'core/presentation';
import { noop } from 'core/utils';

import { createFakeReactSyntheticEvent, isStringArray, orEmptyString } from './utils';
import { IFormInputProps } from '../interface';

interface IReactSelectInputProps extends IFormInputProps, OmitControlledInputPropsFrom<ReactSelectProps> {
  stringOptions?: string[];
  tethered?: boolean;
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
    const target = { name, value: selectedOption.value };
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
    tethered: true,
  };

  public render() {
    const {
      name,
      onChange,
      onBlur,
      value,
      tethered,
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
      tethered ? (
        <TetheredSelect className={className} style={style} options={options} {...fieldProps} {...otherProps} />
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
