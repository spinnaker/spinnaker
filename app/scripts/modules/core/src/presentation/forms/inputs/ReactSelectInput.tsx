import * as React from 'react';
import Select, { Option, ReactSelectProps } from 'react-select';

import { IControlledInputProps, StringsAsOptions, TetheredSelect } from 'core/presentation';
import { noop } from 'core/utils';

import { isStringArray, orEmptyString } from './utils';
import { IFormInputProps } from '../interface';

interface IReactSelectInputProps extends IFormInputProps, ReactSelectProps {
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

export const createFakeReactSyntheticEvent = (target: { name: string; value?: any }) => ({
  persist: noop,
  stopPropagation: noop,
  preventDefault: noop,
  target,
});

/**
 * Given a IControlledInputProps "field" (i.e., from Formik), returns an onChange handler
 * somewhat compatible with the controlled input pattern
 */
export const reactSelectOnChangeAdapter = (field: IControlledInputProps) => {
  return (selectedOption: Option) => {
    const target = { name: field.name, value: selectedOption.value };
    const event = createFakeReactSyntheticEvent(target);
    return (field.onChange || noop)(event);
  };
};

/**
 * Given a IControlledInputProps "field" (i.e., from Formik), returns an onBlur handler
 * somewhat compatible with the controlled input pattern
 */
export const reactSelectOnBlurAdapter = (field: IControlledInputProps) => {
  return () => {
    const target = { name: field.name, value: field.value };
    const event = createFakeReactSyntheticEvent(target);
    return (field.onBlur || noop)(event);
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
      tethered,
      field,
      validation,
      stringOptions,
      options: optionOptions,
      inputClassName,
      ...otherProps
    } = this.props;

    const onChange = reactSelectOnChangeAdapter(field);
    const onBlur = reactSelectOnBlurAdapter(field);
    const fieldProps = { name: field.name, value: orEmptyString(field.value), onBlur, onChange };
    const className = orEmptyString(inputClassName);
    const style = validation.validationStatus === 'error' ? reactSelectValidationErrorStyle : {};

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
