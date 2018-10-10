import * as React from 'react';

import { ValidationMessage } from 'core/validation';

import { FormikFormField, IFormikFieldProps } from '../fields';
import { ExpressionError, ExpressionInput, ExpressionPreview, ISpelError } from '../inputs';
import { ICommonFormFieldProps, IFieldLayoutPropsWithoutInput, IFormInputProps } from '../interface';

export interface IExpressionFieldProps {
  placeholder?: string;
  markdown?: boolean;
  context: object;
  layout?: ICommonFormFieldProps['layout'];
}

export type IFormikExpressionFieldProps = IExpressionFieldProps & IFormikFieldProps & IFieldLayoutPropsWithoutInput;

export interface IFormikExpressionFieldState {
  spelPreview: string;
  spelError: ISpelError;
}

export class FormikExpressionField extends React.Component<IFormikExpressionFieldProps, IFormikExpressionFieldState> {
  public static defaultProps: Partial<IFormikExpressionFieldProps> = { markdown: false };
  public state = { spelPreview: '', spelError: null as ISpelError };

  public render() {
    const { spelError, spelPreview } = this.state;
    const { markdown, context } = this.props;
    const { name, label, error: errorMsg, warning: warningMsg, help, actions } = this.props;

    const error =
      (errorMsg && <ValidationMessage type="error" message={errorMsg} />) ||
      (context && spelError && <ExpressionError spelError={spelError} />);
    const warning = warningMsg && <ValidationMessage type="warning" message={warningMsg} />;
    const preview = spelPreview && <ExpressionPreview spelPreview={spelPreview} markdown={markdown} />;

    return (
      <FormikFormField
        name={name}
        input={(props: IFormInputProps) => (
          <ExpressionInput onExpressionChange={changes => this.setState(changes)} context={context} {...props} />
        )}
        label={label}
        help={help}
        actions={actions}
        error={error || warning || preview}
      />
    );
  }
}
