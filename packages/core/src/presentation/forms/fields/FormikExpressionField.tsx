import React from 'react';

import { FormikFormField, IFormikFormFieldProps } from './FormikFormField';
import { ExpressionError, ExpressionInput, ExpressionPreview, IExpressionChange, ISpelError } from '../inputs';
import { ICommonFormFieldProps } from './interface';
import { firstDefined } from '../../../utils';
import { useValidationData } from '../validation';

export interface IExpressionFieldProps {
  placeholder?: string;
  markdown?: boolean;
  context: object;
  layout?: ICommonFormFieldProps['layout'];
}

export type IFormikExpressionFieldProps = IExpressionFieldProps & IFormikFormFieldProps<string>;

export interface IFormikExpressionFieldState {
  spelPreview: string;
  spelError: ISpelError;
}

const initialSpelData: IExpressionChange = {
  value: null,
  spelError: null,
  spelPreview: null,
};

export function FormikExpressionField(props: IFormikExpressionFieldProps) {
  const { context, name, label, help, actions, validationMessage: message } = props;

  const [spelData, setSpelData] = React.useState<IExpressionChange>(initialSpelData);

  const markdown = firstDefined(props.markdown, false);
  const validationNode = useValidationData(message, true);

  const validationMessage =
    validationNode.messageNode ||
    (spelData.spelError && context && <ExpressionError spelError={spelData.spelError} />) ||
    (spelData.spelPreview && <ExpressionPreview spelPreview={spelData.spelPreview} markdown={markdown} />);

  return (
    <FormikFormField
      name={name}
      input={(inputProps) => (
        <ExpressionInput onExpressionChange={(changes) => setSpelData(changes)} context={context} {...inputProps} />
      )}
      label={label}
      help={help}
      actions={actions}
      validationMessage={validationMessage}
    />
  );
}
