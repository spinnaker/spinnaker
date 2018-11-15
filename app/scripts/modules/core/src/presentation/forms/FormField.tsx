import * as React from 'react';
import { Subject } from 'rxjs';
import { isString } from 'lodash';

import { noop } from 'core/utils';

import { createFieldValidator } from './FormikFormField';
import { renderContent } from './fields/renderContent';
import { StandardFieldLayout } from './layouts/index';
import { Validator } from './Validation';
import { WatchValue } from '../WatchValue';
import {
  ICommonFormFieldProps,
  IControlledInputProps,
  IFieldLayoutPropsWithoutInput,
  IFieldValidationStatus,
  IFormFieldApi,
  IValidationProps,
} from './interface';

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

const ifString = (val: any): string => (isString(val) ? val : undefined);

export class FormField extends React.Component<IFormFieldProps, IFormFieldState> implements IFormFieldApi {
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

  public name = () => this.props.name;

  public label = () => ifString(this.props.label);

  public value = () => this.props.value;

  public touched = () => this.props.touched;

  public validationMessage = () => ifString(this.props.validationMessage) || ifString(this.state.validationMessage);

  public validationStatus = () => this.props.validationStatus || this.state.validationStatus;

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
    const { onChange, onBlur, value, name } = this.props; // IControlledInputProps

    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };
    const controlledInputProps: IControlledInputProps = { onChange, onBlur, value, name };

    const validationProps: IValidationProps = {
      touched: this.touched(),
      validationMessage: this.validationMessage(),
      validationStatus: this.validationStatus(),
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
