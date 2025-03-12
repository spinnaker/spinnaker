import { isEqual } from 'lodash';
import React from 'react';

import { TextInput } from '../TextInput';
import type { IExpressionChange } from './evaluateExpression';
import { evaluateExpression } from './evaluateExpression';
import type { IFormInputProps } from '../interface';

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
