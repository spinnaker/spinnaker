import * as React from 'react';

import { ValidationMessage } from 'core/validation';

import { BasicLayout } from '../layouts';
import { formikField, IFormikField } from './formikField';
import { ExpressionInput, ExpressionError, ExpressionPreview, ISpelError } from '../inputs';
import { IFieldLayoutProps, IFieldProps } from '../interface';

export interface IExpressionFieldProps extends IFieldProps {
  placeholder?: string;
  markdown?: boolean;
  context: object;
}

export interface IExpressionFieldState {
  spelPreview: string;
  spelError: ISpelError;
}

export class ExpressionField extends React.Component<IExpressionFieldProps, IExpressionFieldState> {
  static Formik: IFormikField<IExpressionFieldProps> = formikField(ExpressionField);
  static defaultProps: Partial<IExpressionFieldProps> = { markdown: false, FieldLayout: BasicLayout };
  public state = { spelPreview: '', spelError: null as ISpelError };

  public render() {
    const { spelError, spelPreview } = this.state;
    const { value, onChange, placeholder, markdown, context } = this.props;
    const { FieldLayout, label, error: errorMsg, warning: warningMsg, help, actions } = this.props;

    const input = (
      <ExpressionInput
        value={value}
        onChange={evt => onChange(evt.target.value)}
        onExpressionChange={changes => this.setState(changes)}
        context={context}
        placeholder={placeholder}
      />
    );

    const error =
      (errorMsg && <ValidationMessage type="error" message={errorMsg} />) ||
      (context && spelError && <ExpressionError spelError={spelError} />);
    const warning = warningMsg && <ValidationMessage type="warning" message={warningMsg} />;
    const preview = spelPreview && <ExpressionPreview spelPreview={spelPreview} markdown={markdown} />;

    const layoutProps: IFieldLayoutProps = { label, input, help, actions, error, warning, preview };

    return <FieldLayout {...layoutProps} />;
  }
}
