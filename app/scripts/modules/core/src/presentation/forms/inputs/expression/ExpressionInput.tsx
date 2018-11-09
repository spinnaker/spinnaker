import * as React from 'react';
import { isEqual } from 'lodash';

import { IFormInputProps, TextInput } from 'core/presentation';

import { IExpressionChange, evaluateExpression } from './evaluateExpression';

export interface IExpressionInputProps extends IFormInputProps {
  onExpressionChange: (expressionChange: IExpressionChange) => void;
  placeholder?: string;
  context?: object;
}

export class ExpressionInput extends React.Component<IExpressionInputProps> {
  public static defaultProps = { context: {}, placeholder: '' };

  public componentDidUpdate(prevProps: IExpressionInputProps) {
    const { context, field } = this.props;
    if (field.value !== prevProps.field.value || !isEqual(context, prevProps.context)) {
      const expressionChange = evaluateExpression(context, field.value);
      this.props.onExpressionChange(expressionChange);
    }
  }

  public render(): JSX.Element {
    const { field, placeholder, validation } = this.props;
    return <TextInput autoComplete="off" field={field} placeholder={placeholder} validation={validation} />;
  }
}
