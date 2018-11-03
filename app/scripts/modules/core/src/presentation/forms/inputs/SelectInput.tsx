import * as React from 'react';
import { Option } from 'react-select';

import { StringsAsOptions } from 'core/presentation';

import { isStringArray, orEmptyString, validationClassName } from './utils';
import { IFormInputProps } from '../interface';

interface ISelectInputProps extends IFormInputProps, React.InputHTMLAttributes<any> {
  inputClassName?: string;
  options: Array<string | Option<string>>;
}

export class SelectInput extends React.Component<ISelectInputProps> {
  public render() {
    const { field, validation, options, inputClassName, ...otherProps } = this.props;
    const fieldProps = { ...field, value: orEmptyString(field.value) };
    const className = `SelectInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    const SelectElement = ({ opts }: { opts: Array<Option<string>> }) => (
      <select className={className} {...fieldProps} {...otherProps}>
        {opts.map(option => (
          <option key={option.value} value={option.value}>
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
}
