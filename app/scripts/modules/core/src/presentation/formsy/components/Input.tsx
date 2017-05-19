import * as React from 'react';

import { FormComponent, IFormComponentState, IFormComponentProps } from '../FormComponent';

export interface IProps extends IFormComponentProps {
  className: string;
  label: string;
  type: string;
}

/** A simple Formsy form component for validated <input> tags (text or checkbox) */
export class Input extends FormComponent<string, IProps, IFormComponentState> {
  public handleValueChanged(value: any) {
    this.setValue(value);
  }

  public render() {
    const { name, label, type } = this.props;
    const className = 'form-group' + (this.props.className || ' ') + (this.showRequired() ? 'required' : this.showError() ? 'error' : null);
    const errorMessage = this.getErrorMessage();
    return (
      <div className={className}>
        <label htmlFor={name}>{label}</label>
        <input
          type={type || 'text'}
          name={name}
          onChange={this.handleValueChanged}
          value={this.getValue()}
          checked={!!(type === 'checkbox' && this.getValue())}
        />
        <span className="validation-error">{errorMessage}</span>
      </div>
    );
  }
}
