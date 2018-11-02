import * as React from 'react';
import { Option } from 'react-select';
import { isString } from 'lodash';

import { StringsAsOptions } from 'core/presentation';

import { orEmptyString, validationClassName } from './utils';
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

    const isStringArray = (opts: any[]): opts is string[] => opts && opts.length && opts.every(isString);

    if (isStringArray(options)) {
      return <StringsAsOptions strings={options}>{opts => <SelectElement opts={opts} />}</StringsAsOptions>;
    } else {
      return <SelectElement opts={options as Array<Option<string>>} />;
    }
  }
}
