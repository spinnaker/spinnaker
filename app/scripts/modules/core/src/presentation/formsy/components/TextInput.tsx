import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { FormsyComponent, IFormsyComponentProps } from '../FormsyComponent';

export interface ITextInputProps extends IFormsyComponentProps {
  placeholder?: string;
}

/**
 * A Formsy form component that accepts a LayoutComponent
 */
@BindAll()
export class TextInput extends FormsyComponent<string, ITextInputProps> {
  public static contextTypes = FormsyComponent.contextTypes;
  public static defaultProps = FormsyComponent.defaultProps;

  public renderInput(): JSX.Element {
    const { name, placeholder } = this.props;
    const inputClass = this.getInputClass();

    return (
      <input
        className={inputClass}
        type="text"
        name={name}
        id={name}
        placeholder={placeholder}
        onChange={this.handleChange}
        value={this.getValue() || ''}
      />
    );
  }
}
