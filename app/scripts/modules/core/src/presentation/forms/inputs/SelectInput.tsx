import React from 'react';
import { Option } from 'react-select';

import { StringsAsOptions } from './StringsAsOptions';
import { isStringArray, orEmptyString, validationClassName } from './utils';
import { IFormInputProps, OmitControlledInputPropsFrom } from './interface';

export interface ISelectInputProps
  extends IFormInputProps,
    OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
  options: Array<string | Option<string>>;
}

export function SelectInput(props: ISelectInputProps) {
  const { value, validation, options, inputClassName, ...otherProps } = props;
  const className = `SelectInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  const SelectElement = ({ opts }: { opts: Array<Option<string>> }) => (
    <select className={className} value={orEmptyString(value)} {...otherProps}>
      {opts.map(option => (
        <option key={option.value} value={option.value} disabled={option.disabled}>
          {option.label}
        </option>
      ))}
    </select>
  );

  if (isStringArray(options)) {
    return <StringsAsOptions strings={options}>{opts => <SelectElement opts={opts} />}</StringsAsOptions>;
  } else {
    return <SelectElement opts={options as Array<Option<string>>} />;
  }
}
