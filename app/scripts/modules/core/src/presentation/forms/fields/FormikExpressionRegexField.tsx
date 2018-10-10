import * as React from 'react';
import { get } from 'lodash';

import { CollapsibleSection, CurrentForm, FormikFormField, IFieldLayoutProps, TextInput } from 'core/presentation';
import { ValidationMessage } from 'core/validation';

import { ExpressionInput, ExpressionPreview, ISpelError } from '../inputs';
import { IFormikExpressionFieldProps } from './FormikExpressionField';

export interface IRegexpProps {
  RegexHelp?: JSX.Element;
  regexName: string; // The name of the regex field in formik
  replaceName: string; // The name of the replace field in formik
}

export type IFormikExpressionRegexFieldProps = IFormikExpressionFieldProps & IRegexpProps;

export interface IFormikExpressionRegexFieldState {
  spelPreview: string;
  spelError: ISpelError;
}

export class FormikExpressionRegexField extends React.Component<
  IFormikExpressionRegexFieldProps,
  IFormikExpressionRegexFieldState
> {
  public static defaultProps: Partial<IFormikExpressionFieldProps> = { markdown: false };
  public state: IFormikExpressionRegexFieldState = {
    spelPreview: null,
    spelError: null,
  };

  private renderRegexFields(props: IFormikExpressionRegexFieldProps) {
    const { RegexHelp, regexName, replaceName } = props;

    const sectionHeading = ({ chevron }: { chevron: JSX.Element }) => (
      <span>
        {' '}
        {chevron} Regex {RegexHelp}{' '}
      </span>
    );

    const validateRegexString = (regexString: string): string => {
      try {
        RegExp(regexString);
        return '';
      } catch (error) {
        return error.message;
      }
    };

    const RegexLayout = ({ input }: IFieldLayoutProps) => <div style={{ flex: '1 1 40%' }}> {input} </div>;

    return (
      <CurrentForm
        render={formik => {
          const regex = get(formik.values, regexName);

          return (
            <CollapsibleSection
              heading={sectionHeading}
              outerDivClassName=""
              toggleClassName="clickable"
              defaultExpanded={!!regex}
            >
              <div className="flex-container-h baseline">
                <code>s/</code>
                <FormikFormField
                  name={regexName}
                  validate={validateRegexString}
                  layout={RegexLayout}
                  input={TextInput}
                />
                <code>/</code>
                <FormikFormField name={replaceName} layout={RegexLayout} input={TextInput} />
                <code>/g</code>
              </div>
            </CollapsibleSection>
          );
        }}
      />
    );
  }

  private renderErrorOrPreview(props: IFormikExpressionRegexFieldProps) {
    const { spelPreview, spelError } = this.state;
    const { markdown, regexName, replaceName } = props;

    if (spelError) {
      return <ValidationMessage type="error" message={spelError.message} />;
    }

    return (
      <CurrentForm
        render={formik => {
          const regex: string = get(formik.values, regexName);
          const replace: string = get(formik.values, replaceName);

          if (spelPreview && regex) {
            try {
              const replacedSpelPreview = spelPreview.replace(new RegExp(regex, 'g'), replace);
              return (
                !!replacedSpelPreview && <ExpressionPreview spelPreview={replacedSpelPreview} markdown={markdown} />
              );
            } catch (err) {
              return <ValidationMessage type="error" message={err.message} />;
            }
          }

          return false;
        }}
      />
    );
  }

  public render() {
    const { context, placeholder, help, label, actions } = this.props;

    const regexFields = this.renderRegexFields(this.props);
    const error = this.renderErrorOrPreview(this.props);

    return (
      <FormikFormField
        name={this.props.name}
        input={props => (
          <div className="flex-container-v flex-grow">
            <ExpressionInput
              onExpressionChange={exprChange => this.setState(exprChange)}
              context={context}
              placeholder={placeholder}
              {...props}
            />

            {regexFields}
          </div>
        )}
        help={help}
        label={label}
        actions={actions}
        error={error}
        touched={true}
      />
    );
  }
}
