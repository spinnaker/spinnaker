import { isNil } from 'lodash';
import React, { useEffect } from 'react';
import { Option } from 'react-select';

import { StringsAsOptions } from './StringsAsOptions';
import { IFormInputProps, OmitControlledInputPropsFrom } from './interface';
import { createFakeReactSyntheticEvent, isStringArray, orEmptyString, validationClassName } from './utils';

export interface ISelectInputProps
  extends IFormInputProps,
    OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
  options: Array<string | Option<string>>;
  /**
   * If the value prop does not match any of the options, this value will be used.
   * This can be used to ensures that a valid option is always selected (for initial state, for example).
   * This mechanism calls onChange with the defaultValue.
   * If this prop is used, the options prop provided should be stable (useMemo).
   */
  defaultValue?: string;
}

export function SelectInput(props: ISelectInputProps) {
  const { value, defaultValue, validation, options, inputClassName, ...otherProps } = props;
  const className = `SelectInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  useEffect(() => {
    if (!isNil(defaultValue)) {
      const values = isStringArray(options) ? options : (options as Array<Option<string>>).map((o) => o.value);
      if (!values.includes(value)) {
        props.onChange(createFakeReactSyntheticEvent({ name: props.name, value: defaultValue }));
      }
    }
  }, [value, defaultValue, options]);

  const SelectElement = ({ opts }: { opts: Array<Option<string>> }) => (
    <select className={className} value={orEmptyString(value)} {...otherProps}>
      {opts.map((option) => (
        <option key={option.value} value={option.value} disabled={option.disabled}>
          {option.label}
        </option>
      ))}
    </select>
  );

  if (isStringArray(options)) {
    return <StringsAsOptions strings={options}>{(opts) => <SelectElement opts={opts} />}</StringsAsOptions>;
  } else {
    return <SelectElement opts={options as Array<Option<string>>} />;
  }
}
