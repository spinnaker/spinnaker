import { CollapsibleSection } from 'core/presentation';
import { ValidationMessage } from 'core/validation';
import * as React from 'react';

import { ExpressionError, ExpressionInput, ExpressionPreview, ISpelError } from '../inputs';
import { IFieldLayoutProps } from '../interface';
import { BasicLayout } from '../layouts';
import { IExpressionFieldProps } from './ExpressionField';
import { formikField, IFormikField } from './formikField';

export interface IRegexpProps {
  RegexHelp?: JSX.Element;
  regex: string;
  replace: string;
  onRegexChanged: (regex: string) => void;
  onReplaceChanged: (replace: string) => void;
}

export type IExpressionRegexFieldProps = IRegexpProps & IExpressionFieldProps;

export interface IExpressionRegexFieldState {
  spelPreview: string;
  spelError: ISpelError;
}

export class ExpressionRegexField extends React.Component<IExpressionRegexFieldProps, IExpressionRegexFieldState> {
  public static Formik: IFormikField<IExpressionRegexFieldProps> = formikField<IExpressionRegexFieldProps>(
    ExpressionRegexField,
  );
  public static defaultProps: Partial<IExpressionFieldProps> = { markdown: false, FieldLayout: BasicLayout };
  public state: IExpressionRegexFieldState = {
    spelPreview: null,
    spelError: null,
  };

  private renderRegexFields(props: IExpressionRegexFieldProps) {
    const { RegexHelp, regex, replace, onRegexChanged, onReplaceChanged } = props;

    return (
      <CollapsibleSection
        heading={({ chevron }) => (
          <span>
            {chevron} Regex {RegexHelp}
          </span>
        )}
        outerDivClassName=""
        toggleClassName="clickable"
        defaultExpanded={!!regex}
      >
        <div className="flex-container-h baseline">
          <code>s/</code>
          <input
            type="text"
            value={regex || ''}
            onChange={evt => onRegexChanged(evt.target.value)}
            className="flex-grow"
          />
          <code>/</code>
          <input
            type="text"
            value={replace || ''}
            onChange={evt => onReplaceChanged(evt.target.value)}
            className="flex-grow"
          />
          <code>/g</code>
        </div>
      </CollapsibleSection>
    );
  }

  public render() {
    const { spelError, spelPreview } = this.state;
    const { value, onChange, placeholder, markdown, context, FieldLayout, regex, replace } = this.props;

    let finalSpelPreview = spelPreview;
    let regexError: string = null;

    if (spelPreview && regex) {
      try {
        finalSpelPreview = spelPreview.replace(new RegExp(regex, 'g'), replace);
      } catch (err) {
        regexError = err.toString();
      }
    }

    const expressionInput = (
      <ExpressionInput
        value={value}
        onChange={evt => onChange(evt.target.value)}
        onExpressionChange={exprChange => this.setState(exprChange)}
        context={context}
        placeholder={placeholder}
      />
    );

    const regexFields = this.renderRegexFields(this.props);

    const input = (
      <div className="flex-container-v flex-grow">
        {expressionInput}
        {regexFields}
      </div>
    );

    const { label, help, actions, error: externalError, warning: externalWarning } = this.props;

    const error =
      (externalError && <ValidationMessage type="error" message={externalError} />) ||
      (context && spelError && <ExpressionError spelError={spelError} />) ||
      (context && regexError && <ValidationMessage type="error" message={regexError} />);
    const warning = externalWarning && <ValidationMessage type="warning" message={externalWarning} />;
    const preview = finalSpelPreview && <ExpressionPreview spelPreview={finalSpelPreview} markdown={markdown} />;

    const layoutProps: IFieldLayoutProps = { label, input, help, actions, error, warning, preview };

    return <FieldLayout {...layoutProps} />;
  }
}
