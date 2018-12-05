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
    const { context, value } = this.props;
    if (value !== prevProps.value || !isEqual(context, prevProps.context)) {
      const expressionChange = evaluateExpression(context, value);
      this.props.onExpressionChange(expressionChange);
    }
  }

  public render(): JSX.Element {
    return <TextInput autoComplete="off" {...this.props} />;
  }
}
