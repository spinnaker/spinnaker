import * as React from 'react';
import { Subject } from 'rxjs';

import { noop } from 'core/utils';

import { createFieldValidator } from './FormikFormField';
import { renderContent } from './renderContent';
import { StandardFieldLayout } from '../layouts';
import { Validator } from '../Validation';
import { WatchValue } from '../../WatchValue';
import {
  ICommonFormFieldProps,
  IControlledInputProps,
  IFieldLayoutPropsWithoutInput,
  IFieldValidationStatus,
  IValidationProps,
} from '../interface';

export interface IFormFieldValidationProps {
  validate?: Validator | Validator[];
}

export type IFormFieldProps = IFormFieldValidationProps &
  ICommonFormFieldProps &
  Partial<IControlledInputProps> &
  IFieldLayoutPropsWithoutInput &
  IValidationProps;

interface IFormFieldState {
  validationMessage: IValidationProps['validationMessage'];
  validationStatus: IValidationProps['validationStatus'];
  internalValidators: Validator[];
}

export class FormField extends React.Component<IFormFieldProps, IFormFieldState> {
  public static defaultProps: Partial<IFormFieldProps> = {
    layout: StandardFieldLayout,
    validate: noop,
    onBlur: noop,
    onChange: noop,
    name: null,
  };

  public state: IFormFieldState = {
    validationMessage: undefined,
    validationStatus: undefined,
    internalValidators: [],
  };

  private destroy$ = new Subject();
  private value$ = new Subject();

  private addValidator = (internalValidator: Validator) => {
    this.setState(prevState => ({
      internalValidators: prevState.internalValidators.concat(internalValidator),
    }));
  };

  private removeValidator = (internalValidator: Validator) => {
    this.setState(prevState => ({
      internalValidators: prevState.internalValidators.filter(x => x !== internalValidator),
    }));
  };

  public componentDidMount() {
    this.value$
      .distinctUntilChanged()
      .takeUntil(this.destroy$)
      .subscribe(value => {
        const { label, required, validate } = this.props;
        const { internalValidators } = this.state;
        const validator = createFieldValidator(label, required, [].concat(validate).concat(internalValidators));
        Promise.resolve(validator(value)).then(error => {
          const validationMessage: string = !!error ? error : undefined;
          const validationStatus: IFieldValidationStatus = !!validationMessage ? 'error' : undefined;
          this.setState({ validationMessage, validationStatus });
        });
      });
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public render() {
    const { input, layout } = this.props; // ICommonFormFieldProps
    const { label, help, required, actions } = this.props; // IFieldLayoutPropsWithoutInput
    const { touched, validationMessage: message, validationStatus: status } = this.props; // IValidationProps
    const { onChange, onBlur, value, name } = this.props; // IControlledInputProps

    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };
    const controlledInputProps: IControlledInputProps = { onChange, onBlur, value, name };

    const validationMessage = message || this.state.validationMessage;
    const validationStatus = status || this.state.validationStatus;
    const validationProps: IValidationProps = {
      touched,
      validationMessage,
      validationStatus,
      addValidator: this.addValidator,
      removeValidator: this.removeValidator,
    };

    const inputElement = renderContent(input, { field: controlledInputProps, validation: validationProps });

    return (
      <WatchValue onChange={x => this.value$.next(x)} value={value}>
        {renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement })}
      </WatchValue>
    );
  }
}
