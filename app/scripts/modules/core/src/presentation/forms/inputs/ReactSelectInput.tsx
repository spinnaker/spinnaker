import * as React from 'react';
import Select, { Option, ReactSelectProps } from 'react-select';

import { noop } from 'core/utils';
import { IControlledInputProps, TetheredSelect } from 'core/presentation';

import { IFormInputProps } from '../interface';

interface IReactSelectInputProps extends IFormInputProps, ReactSelectProps {
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
export const reactSelectOnChangeAdapter = (field: IControlledInputProps) => {
  return (selectedOption: Option) => {
    const fakeEvent = {
      target: {
        value: selectedOption.value,
        name: field.name,
      },
      persist: noop,
      stopPropagation: noop,
      preventDefault: noop,
    };
    return (field.onChange || noop)(fakeEvent);
  };
};

/**
 * A react-select Input
 *
 * This input supports error validation state rendering
 * It also has a simplified stringOptions prop for when Option interface is overkill.
 * It adapts the onChange event to a controlled input event
 *
 * This component does not attempt to support async loading
 */
export class ReactSelectInput extends React.Component<IReactSelectInputProps> {
  public static defaultProps: Partial<IReactSelectInputProps> = {
    tethered: true,
  };

  public render() {
    const { tethered, field, validation, inputClassName, ...otherProps } = this.props;

    // TODO: see if implementing onBlur makes sense
    const onChange = reactSelectOnChangeAdapter(field);
    const fieldProps = { name: field.name, value: field.value || '', onBlur: noop, onChange };
    const style = validation.validationStatus === 'error' ? reactSelectValidationErrorStyle : {};

    return tethered ? (
      <TetheredSelect className={inputClassName} style={style} {...fieldProps} {...otherProps} />
    ) : (
      <Select className={inputClassName} style={style} {...fieldProps} {...otherProps} />
    );
  }
}
