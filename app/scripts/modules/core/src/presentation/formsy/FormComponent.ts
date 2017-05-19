// https://github.com/christianalfoni/formsy-react/issues/191#issuecomment-144872142

import { Mixin, ValidationErrors } from 'formsy-react';
import { omit } from 'lodash';
import * as React from 'react';

/*
 * Here the Formsy.Mixin is wrapped as an abstract class, so that it can
 * be used effectively with TypeScript.
 */
export interface IFormComponentProps {
  name?: string;
  value?: string | string[] | number;
  validations?: string;
  validationError?: string;
  validationErrors?: ValidationErrors;
  required?: boolean;
  _validate?: Function;
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
export abstract class FormComponent<VALUE, PROPS extends IFormComponentProps, STATE extends IFormComponentState> extends React.Component<PROPS, STATE> {
  public static propnames: (keyof IFormComponentProps)[] = ['name', 'value', 'validations', 'validationError', 'validationErrors', 'required', '_validate'];

  public static defaultProps: IFormComponentProps = {
    name: null,  // this can be set to whatever, since it will be overwritten when child components are created
    validationError: '',
    validationErrors: {}
  };

  public static otherProps(props: any): any {
    return omit(props, FormComponent.propnames);
  }

  constructor(props: PROPS, context: any) {
    super(props, context);

    // Default values for state
    this.state = Mixin.getInitialState.call(this) as STATE;
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
