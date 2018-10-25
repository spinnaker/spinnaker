import * as React from 'react';

import { orEmptyString, validationClassName } from './utils';
import { IFormInputProps } from '../interface';

interface ITextInputProps extends IFormInputProps, React.InputHTMLAttributes<any> {
  inputClassName?: string;
}

export class TextInput extends React.Component<ITextInputProps> {
  public render() {
    const { field, validation, inputClassName, ...otherProps } = this.props;
    const fieldProps = { ...field, value: orEmptyString(field.value) };
    const className = `TextInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    return <input className={className} type="text" autoComplete="off" {...fieldProps} {...otherProps} />;
  }
}
