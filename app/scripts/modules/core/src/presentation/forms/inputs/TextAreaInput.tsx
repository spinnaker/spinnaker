import * as React from 'react';

import { orEmptyString, validationClassName } from './utils';
import { IFormInputProps } from '../interface';

interface ITextAreaInputProps extends IFormInputProps, React.TextareaHTMLAttributes<any> {
  inputClassName?: string;
}

export class TextAreaInput extends React.Component<ITextAreaInputProps> {
  public render() {
    const { field, validation, inputClassName, ...otherProps } = this.props;
    const fieldProps = { ...field, value: orEmptyString(field.value) };
    const className = `TextAreaInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    return <textarea className={className} autoComplete="off" {...fieldProps} {...otherProps} />;
  }
}
