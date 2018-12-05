import * as React from 'react';
import { Option } from 'react-select';

import { OmitControlledInputPropsFrom, StringsAsOptions } from 'core/presentation';

import { isStringArray, orEmptyString, validationClassName } from './utils';
import { IFormInputProps } from '../interface';

interface ISelectInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
  options: Array<string | Option<string>>;
}

export class SelectInput extends React.Component<ISelectInputProps> {
  public render() {
    const { value, validation, options, inputClassName, ...otherProps } = this.props;
    const className = `SelectInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    const SelectElement = ({ opts }: { opts: Array<Option<string>> }) => (
      <select className={className} value={orEmptyString(value)} {...otherProps}>
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
