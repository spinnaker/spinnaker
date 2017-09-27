import * as React from 'react';
import * as spel2js from 'spel2js';
import { BindAll } from 'lodash-decorators';
import { truncate } from 'lodash';

import { FormsyComponent, IFormsyComponentProps, IFormComponentState } from '../FormsyComponent';
import { Markdown } from 'core/presentation/Markdown';

import '../spel2js.templateParser';
import { Modal } from 'react-bootstrap';

export interface IExpressionInputProps extends IFormsyComponentProps {
  placeholder?: string;
  context?: object;
  locals?: object;
  markdown?: boolean;
}

export interface IExpressionInputState extends IFormComponentState {
  showContextModal: boolean;
  spelPreview: string;
  spelError: ISpelError;
}

export interface ISpelError {
  message: string;
  context: string;
  contextTruncated: string;
}

/**
 * A validating Formsy form component for SpEL Expressions
 */
@BindAll()
export class ExpressionInput extends FormsyComponent<string, IExpressionInputProps, IExpressionInputState> {
  public static contextTypes = FormsyComponent.contextTypes;
  public static defaultProps = Object.assign({ context: {}, locals: {} }, FormsyComponent.defaultProps);

  public validate(props: IExpressionInputProps = this.props): boolean {
    const value = this.getValue();
    const { context, locals } = props;

    const stringify = (obj: any): string => {
      return obj === null ? 'null' :
        obj === undefined ? 'undefined' :
          JSON.stringify(obj, null, 2);
    };

    try {
      if (!value) {
        return true;
      }

      const exprs = spel2js.TemplateParser.parse(value);
      const results = exprs.map(expr => expr.eval(context, locals));
      this.setState({ spelError: null, spelPreview: results.join('') });

      return true;
    } catch (err) {
      const spelError: ISpelError = {
        message: null,
        context: null,
        contextTruncated: null,
      };

      if (err.name && err.message) {
        if (err.name === 'NullPointerException' && err.state && err.state.activeContext) {
          spelError.context = stringify(err.state.activeContext.peek());
          spelError.contextTruncated = truncate(spelError.context, { length: 200 });
        }
        spelError.message = `${err.name}: ${err.message}`;
      } else {
        try {
          spelError.message = JSON.stringify(err);
        } catch (ignored) {
          spelError.message = err.toString();
        }
      }

      this.setState({ spelError, spelPreview: null });
      return false;
    }
  }

  public componentWillReceiveProps(nextProps: IExpressionInputProps): void {
    this.validate(nextProps);
  }

  public getErrorMessage(): string {
    const { spelError } = this.state;
    if (spelError) {
      return spelError.message + (spelError.contextTruncated ? ' -- ' + spelError.contextTruncated : '');
    }

    return super.getErrorMessage();
  }

  private hideContextModal(): void {
    this.setState({ showContextModal: false })
  }

  private showContextModal(): void {
    this.setState({ showContextModal: true })
  }

  public renderError(): JSX.Element {
    const { spelError } = this.state;

    return (
      <div>
        {super.renderError()}

        {spelError && spelError.message && (
          <Modal show={this.state.showContextModal} onHide={this.hideContextModal}>
            <Modal.Header>
              <h3>{this.state.spelError.message}</h3>
            </Modal.Header>

            <Modal.Body>
              <pre>{this.state.spelError.context}</pre>
            </Modal.Body>

            <Modal.Footer>
              <button className="btn btn-primary" type="button" onClick={this.hideContextModal}>Close</button>
            </Modal.Footer>
          </Modal>
        )}

        {spelError && spelError.message && (
          <span className="link clickable" onClick={this.showContextModal}>Show SpEL error details</span>
        )}
      </div>
    )
  }

  public renderInput(): JSX.Element {
    const { name, placeholder, markdown } = this.props;
    const { spelPreview } = this.state;
    const inputClass = this.getInputClass();

    return (
      <div className="flex-container-v">
        <input
          autoComplete="off"
          className={inputClass}
          type="text"
          name={name}
          id={name}
          placeholder={placeholder}
          onChange={this.handleChange}
          value={this.getValue() || ''}
        />

        { spelPreview && (
          <div className="flex-container-h baseline margin-between">
            <span className="no-grow">Preview:</span> {markdown ? <Markdown message={spelPreview} /> : <span>{spelPreview}</span>}
          </div>
        )}
      </div>
    )
  }
}
