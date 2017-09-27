// https://github.com/christianalfoni/formsy-react/issues/191#issuecomment-144872142

import * as React from 'react';
import { Requireable, PropTypes, ValidationMap, ChangeEvent } from 'react';
import { Mixin, ValidationErrors } from 'formsy-react';
import { BindAll } from 'lodash-decorators';
import * as classNames from 'classnames';

import { LayoutComponent } from './layouts/formFieldLayout';
import { noop } from 'core/utils';

/*
 * Here the Formsy.Mixin is wrapped as an abstract class, so that it can
 * be used effectively with TypeScript.
 */
export interface IFormsyComponentProps {
  /** The name of the form field */
  name?: string;
  /** The value bound to the form field */
  value?: string | string[] | number;
  /** The className to apply (can be used by the renderInput method) */
  className?: string;

  /** The (optional) label text for the form field */
  label?: string;
  /** (optional) The help or usage rollover markup */
  Help?: React.ReactElement<any>;
  /** A react class that will layout the components returned from
   * renderInput, renderError, renderLabel and the Help component */
  Layout?: LayoutComponent;

  validations?: string;
  validationError?: string;
  validationErrors?: ValidationErrors;
  required?: boolean;
  _validate?: Function;

  /** An (optional) callback for when the form field value changes */
  onChange?(event: ChangeEvent<any>): void;
}

export interface IFormComponentState {
  _value: any;
  _isRequired: boolean;
  _isValid: boolean;
  _isPristine: boolean;
  _pristineValue: any,
  _validationError: string;
  _externalError: string;
  _formSubmitted: boolean;
}

/** A base class for Formsy form components */
@BindAll()
export abstract class FormsyComponent
    <
      VALUE = string,
      PROPS extends IFormsyComponentProps = IFormsyComponentProps,
      STATE extends IFormComponentState = IFormComponentState
    >
    extends React.Component<PROPS, STATE> {

  public static contextTypes: ValidationMap<{ formsy: Requireable<any> }> = {
    formsy: PropTypes.object,
  };

  // Typing as `any` to avoid typing errors like:
  // exported class has or is using name 'IFormsyComponentProps' but cannot be named
  public static defaultProps: IFormsyComponentProps & any = {
    name: null,  // this can be set to whatever, since it will be overwritten when child components are created
    className: '',
    validationError: '',
    validationErrors: {},
    onChange: noop,
  };

  constructor(props: PROPS, context: any) {
    super(props, context);

    // Default values for state
    this.state = Mixin.getInitialState.call(this) as STATE;
  }

  public handleChange(event: ChangeEvent<any>): void {
    this.setValue(event.target.value);
    this.props.onChange(event);
  }

  public getInputClass(): string {
    const className: string = this.props.className;
    return classNames({
      'form-control': true,
      'ng-invalid': this.showError() || this.showRequired(),
      'ng-dirty': !this.isPristine(),
      [className]: !!className
    });
  }

  public abstract renderInput(): JSX.Element;

  public renderLabel(): JSX.Element {
    const { label, name } = this.props;

    return label && <label htmlFor={name}>{label}</label>;
  }

  public renderError(): JSX.Element {
    const errorMessage = this.getErrorMessage();
    const isDirty = !this.isPristine();

    return errorMessage && isDirty && <span className="error-message">{errorMessage}</span>;
  }

  public render(): JSX.Element {
    const Layout: LayoutComponent = this.props.Layout;
    const HelpElement: JSX.Element = this.props.Help;
    const Label = this.renderLabel();
    const Input = this.renderInput();
    const Error = this.renderError();

    return (
      <Layout
        Label={Label}
        Input={Input}
        Help={HelpElement}
        Error={Error}
        showRequired={this.showRequired()}
        showError={this.showError()}
      />
    );
  }

  // Lifecycle methods
  public componentWillMount(): void {
    Mixin.componentWillMount.call(this);
  }

  public componentWillReceiveProps(nextProps: PROPS): void {
    Mixin.componentWillReceiveProps.call(this, nextProps);
  }

  public componentDidUpdate(prevProps: PROPS): void {
    Mixin.componentDidUpdate.call(this, prevProps);
  }

  public componentWillUnmount(): void {
    Mixin.componentWillUnmount.call(this);
  }

  // Formsy methods
  public setValue(value: VALUE): void {
    Mixin.setValue.call(this, value);
  }

  public resetvalue(): void {
    Mixin.resetValue.call(this);
  }

  public getValue(): VALUE {
    return Mixin.getValue.call(this);
  }

  public hasValue(): boolean {
    return Mixin.hasValue.call(this);
  }

  public getErrorMessage(): string {
    return Mixin.getErrorMessage.call(this);
  }

  public getErrorMessages(): string {
    return Mixin.getErrorMessages.call(this);
  }

  public isFormDisabled(): boolean {
    return Mixin.isFormDisabled.call(this);
  }

  public isValid(): boolean {
    return Mixin.isValid.call(this);
  }

  public isPristine(): boolean {
    return Mixin.isPristine.call(this);
  }

  public isFormSubmitted(): boolean {
    return Mixin.isFormSubmitted.call(this);
  }

  public isRequired(): boolean {
    return Mixin.isRequired.call(this);
  }

  public showRequired(): boolean {
    return Mixin.showRequired.call(this);
  }

  public showError(): boolean {
    return Mixin.showError.call(this);
  }

  public isValidValue(value: any): boolean {
    return Mixin.isValidValue.call(this, value);
  }

  public setValidations(validations: string, required: boolean) {
    Mixin.setValidations.call(this, validations, required);
  }
}
