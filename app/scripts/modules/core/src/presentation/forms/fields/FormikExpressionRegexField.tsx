import * as React from 'react';
import { getIn } from 'formik';

import {
  CollapsibleSection,
  ExpressionError,
  FormikForm,
  FormikFormField,
  IFieldLayoutProps,
  IFieldValidationStatus,
  TextInput,
} from 'core/presentation';
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

  private renderRegexFields(props: IFormikExpressionRegexFieldProps, defaultExpanded: boolean) {
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
      <CollapsibleSection
        heading={sectionHeading}
        outerDivClassName=""
        toggleClassName="clickable"
        defaultExpanded={defaultExpanded}
      >
        <div className="flex-container-h baseline">
          <code>s/</code>
          <FormikFormField
            name={regexName}
            validate={validateRegexString}
            layout={RegexLayout}
            input={TextInput}
            touched={true}
          />
          <code>/</code>
          <FormikFormField name={replaceName} layout={RegexLayout} input={TextInput} />
          <code>/g</code>
        </div>
      </CollapsibleSection>
    );
  }

  private renderPreview(
    props: IFormikExpressionRegexFieldProps,
    spelPreview: string,
    regex: string,
    replace: string,
  ): React.ReactNode {
    const { markdown } = props;

    if (!regex) {
      return <ExpressionPreview spelPreview={spelPreview} markdown={markdown} />;
    }

    try {
      const replacedSpelPreview = spelPreview.replace(new RegExp(regex, 'g'), replace);
      return !!replacedSpelPreview && <ExpressionPreview spelPreview={replacedSpelPreview} markdown={markdown} />;
    } catch (err) {
      // Fallback when regex error -- shouldn't happen with formik 1.x
      return <ExpressionPreview spelPreview={spelPreview} markdown={markdown} />;
    }
  }

  private renderFormField(validationMessage: React.ReactNode, validationStatus: IFieldValidationStatus, regex: string) {
    const { context, placeholder, help, label, actions } = this.props;

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

            {this.renderRegexFields(this.props, !!regex)}
          </div>
        )}
        help={help}
        label={label}
        actions={actions}
        validationMessage={validationMessage}
        validationStatus={validationStatus}
        touched={true}
      />
    );
  }

  public render() {
    const { spelPreview, spelError } = this.state;
    const { regexName, replaceName } = this.props;

    return (
      <FormikForm
        render={formik => {
          const regex: string = getIn(formik.values, regexName);
          const replace: string = getIn(formik.values, replaceName);

          const regexError = getIn(formik.errors, regexName) || getIn(formik.errors, replaceName);

          if (regexError) {
            const message = <ValidationMessage type="error" message={regexError} />;
            return this.renderFormField(message, 'error', regex);
          } else if (spelError) {
            const message = <ExpressionError spelError={spelError} />;
            return this.renderFormField(message, 'error', regex);
          } else if (spelPreview) {
            const message = this.renderPreview(this.props, spelPreview, regex, replace);
            return this.renderFormField(message, 'message', regex);
          }

          return this.renderFormField(null, 'message', regex);
        }}
      />
    );
  }
}
