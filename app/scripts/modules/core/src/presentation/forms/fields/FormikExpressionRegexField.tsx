import { getIn } from 'formik';
import React from 'react';

import { IFormikExpressionFieldProps } from './FormikExpressionField';
import { FormikForm } from '../FormikForm';
import { FormikFormField } from './FormikFormField';
import { CollapsibleSection } from '../../collapsibleSection/CollapsibleSection';
import { ExpressionInput, ExpressionPreview, ISpelError } from '../inputs';
import { TextInput } from '../inputs/TextInput';
import { ExpressionError } from '../inputs/expression/ExpressionError';
import { ILayoutProps } from '../layouts';
import { errorMessage, messageMessage } from '../validation/categories';

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

  private renderRegexFields(fieldProps: IFormikExpressionRegexFieldProps, defaultExpanded: boolean) {
    const { RegexHelp, regexName, replaceName } = fieldProps;

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

    const RegexLayout = ({ input }: ILayoutProps) => <div style={{ flex: '1 1 40%' }}> {input} </div>;

    return (
      <CollapsibleSection
        heading={sectionHeading}
        outerDivClassName=""
        toggleClassName=""
        defaultExpanded={defaultExpanded}
      >
        <div className="flex-container-h baseline">
          <code>s/</code>
          <FormikFormField
            name={regexName}
            validate={validateRegexString}
            layout={(props) => <RegexLayout {...props} />}
            input={(props) => <TextInput {...props} />}
            touched={true}
          />
          <code>/</code>
          <FormikFormField
            name={replaceName}
            layout={(props) => <RegexLayout {...props} />}
            input={(props) => <TextInput {...props} />}
          />
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

  private renderFormField(validationMessage: React.ReactNode, regex: string) {
    const { context, placeholder, help, label, actions } = this.props;

    return (
      <FormikFormField
        name={this.props.name}
        input={(props) => (
          <div className="flex-container-v flex-grow">
            <ExpressionInput
              onExpressionChange={(exprChange) => this.setState(exprChange)}
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
        touched={true}
      />
    );
  }

  public render() {
    const { spelPreview, spelError } = this.state;
    const { regexName, replaceName } = this.props;

    return (
      <FormikForm
        render={(formik) => {
          const regex: string = getIn(formik.values, regexName);
          const replace: string = getIn(formik.values, replaceName);

          const regexError = getIn(formik.errors, regexName) || getIn(formik.errors, replaceName);

          if (regexError) {
            return this.renderFormField(errorMessage(regexError), regex);
          } else if (spelError) {
            return this.renderFormField(<ExpressionError spelError={spelError} />, regex);
          } else if (spelPreview) {
            const message = this.renderPreview(this.props, spelPreview, regex, replace);
            return this.renderFormField(message, regex);
          }

          return this.renderFormField(messageMessage(null), regex);
        }}
      />
    );
  }
}
