import * as React from 'react';
import { Subject } from 'rxjs';
import { isString } from 'lodash';

import { noop } from 'core/utils';

import { createFieldValidator } from './FormikFormField';
import { renderContent } from './fields/renderContent';
import { LayoutConsumer } from './layouts/index';
import { IValidator } from './validation';
import { WatchValue } from '../WatchValue';
import {
  ICommonFormFieldProps,
  IControlledInputProps,
  IFieldLayoutPropsWithoutInput,
  IFieldValidationStatus,
  IFormFieldApi,
  IValidationProps,
} from './interface';

import './forms.less';

export interface IFormFieldValidationProps {
  validate?: IValidator | IValidator[];
}

export type IFormFieldProps = IFormFieldValidationProps &
  ICommonFormFieldProps &
  Partial<IControlledInputProps> &
  IFieldLayoutPropsWithoutInput &
  IValidationProps;

interface IFormFieldState {
  validationMessage: IValidationProps['validationMessage'];
  validationStatus: IValidationProps['validationStatus'];
  internalValidators: IValidator[];
}

const ifString = (val: any): string => (isString(val) ? val : undefined);

export class FormField extends React.Component<IFormFieldProps, IFormFieldState> implements IFormFieldApi {
  public static defaultProps: Partial<IFormFieldProps> = {
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

  private addValidator = (internalValidator: IValidator) => {
    this.setState(prevState => ({
      internalValidators: prevState.internalValidators.concat(internalValidator),
    }));
  };

  private removeValidator = (internalValidator: IValidator) => {
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
          const validationMessage: string = error ? error : undefined;
          const validationStatus: IFieldValidationStatus = validationMessage ? 'error' : undefined;
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

    const inputElement = renderContent(input, { ...controlledInputProps, validation: validationProps });

    return (
      <WatchValue onChange={x => this.value$.next(x)} value={value}>
        <LayoutConsumer>
          {contextLayout =>
            renderContent(layout || contextLayout, {
              ...fieldLayoutPropsWithoutInput,
              ...validationProps,
              input: inputElement,
            })
          }
        </LayoutConsumer>
      </WatchValue>
    );
  }
}
