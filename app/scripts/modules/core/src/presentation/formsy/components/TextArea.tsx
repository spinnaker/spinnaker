import * as React from 'react';
import { ChangeEvent, PropTypes, ValidationMap } from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { IFormsyContext } from 'formsy-react';

import { FormComponent, IFormComponentProps, IFormComponentState } from '../FormComponent';
import { IFormFieldLayoutProps } from 'core/presentation';
import { noop } from 'core/utils';

export interface ITextAreaProps extends IFormComponentProps, React.HTMLAttributes<HTMLTextAreaElement> {
  /** A react class that will layout the label, input, help, and validation error components */
  Layout: React.ComponentClass<IFormFieldLayoutProps>;
  /** The label text for the textarea */
  label?: string;
  /** (optional) The help or usage rollover markup */
  Help?: React.ReactElement<any>;
  /** The class string to place on the textarea */
  className?: string;
  /** A callback for when the textarea value changes */
  onChange?(event: ChangeEvent<HTMLTextAreaElement>): void;
}

export interface ITextAreaState extends IFormComponentState { }

export interface ITextAreaContext {
  formsy: IFormsyContext;
}

/**
 * A Formsy form component that accepts a LayoutComponent
 */
@autoBindMethods()
export class TextArea extends FormComponent<string, ITextAreaProps, ITextAreaState> {
  public static contextTypes: ValidationMap<ITextAreaContext> = {
    formsy: PropTypes.object,
  };

  public static defaultProps: Partial<ITextAreaProps> = {
    name: null as any,
    onChange: noop,
    className: '',
  };

  public changeValue(event: ChangeEvent<HTMLTextAreaElement>): void {
    this.setValue(event.target.value);
    this.props.onChange(event);
  }

  public render() {
    const { label, Help, Layout, name, className, rows } = this.props;

    const Label = label && <label htmlFor={name}>{label}</label>;

    const isInvalid = this.showError() || this.showRequired();
    const isDirty = !this.isPristine();
    const inputClass = `form-control ${className} ${isInvalid ? 'ng-invalid' : ''} ${isDirty ? 'ng-dirty' : ''}`;
    const Input = <textarea className={inputClass} rows={rows} name={name} onChange={this.changeValue} value={this.getValue()} />;

    const errorMessage = this.getErrorMessage();
    const Error = errorMessage && isDirty && <span className="error-message">{errorMessage}</span>;

    const FormFieldLayout = Layout;
    return (
      <FormFieldLayout
        showRequired={this.showRequired()}
        showError={this.showError()}
        Label={Label}
        Input={Input}
        Help={Help}
        Error={Error}
      />
    );
  }
}

