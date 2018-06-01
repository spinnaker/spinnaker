import * as React from 'react';
import { parseSpelExpressions } from '../spel2js.templateParser';
import { truncate } from 'lodash';

import { Markdown } from 'core/presentation/Markdown';

import { Modal } from 'react-bootstrap';

export interface IExpressionInputProps {
  value?: string;
  onChange: (event: React.ChangeEvent<any>) => void;
  placeholder?: string;
  context?: object;
  locals?: object;
  markdown?: boolean;
  Help?: JSX.Element;
  label: string;
  required?: boolean;
}

export interface IExpressionInputState {
  showContextModal: boolean;
  value: string;
}

export interface ISpelError {
  message: string;
  context: string;
  contextTruncated: string;
}

export class ExpressionInput extends React.Component<IExpressionInputProps, IExpressionInputState> {
  public static defaultProps = { context: {}, locals: {} };

  constructor(props: IExpressionInputProps) {
    super(props);
    this.state = {
      showContextModal: false,
      value: props.value || '',
    };
  }

  private evaluateExpression(
    context: object,
    locals: object,
    value: string,
  ): { spelError: ISpelError; spelPreview: string } {
    if (!value) {
      return { spelError: null, spelPreview: '' };
    }

    const stringify = (obj: any): string => {
      return obj === null ? 'null' : obj === undefined ? 'undefined' : JSON.stringify(obj, null, 2);
    };

    try {
      const exprs = parseSpelExpressions(value);
      const results = exprs.map(expr => expr.eval(context, locals));
      return { spelError: null, spelPreview: results.join('') };
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

      return { spelError, spelPreview: null };
    }
  }

  private getErrorMessage(spelError: ISpelError): string {
    if (spelError) {
      return spelError.message + (spelError.contextTruncated ? ' -- ' + spelError.contextTruncated : '');
    }
    return null;
  }

  private hideContextModal = (): void => {
    this.setState({ showContextModal: false });
  };

  private showContextModal = (): void => {
    this.setState({ showContextModal: true });
  };

  private renderError(spelError: ISpelError): JSX.Element {
    return (
      <div>
        {spelError &&
          spelError.message && (
            <Modal show={this.state.showContextModal} onHide={this.hideContextModal}>
              <Modal.Header>
                <h3>{spelError.message}</h3>
              </Modal.Header>

              <Modal.Body>
                <pre>{spelError.context}</pre>
              </Modal.Body>

              <Modal.Footer>
                <button className="btn btn-primary" type="button" onClick={this.hideContextModal}>
                  Close
                </button>
              </Modal.Footer>
            </Modal>
          )}

        {spelError &&
          spelError.message && (
            <span className="link clickable" onClick={this.showContextModal}>
              Show SpEL error details
            </span>
          )}
      </div>
    );
  }

  private handleChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.setState({ value: event.target.value });
    this.props.onChange(event);
  };

  public render(): JSX.Element {
    const { Help, label, markdown, placeholder, required, context, locals } = this.props;
    const { value } = this.state;
    const { spelError, spelPreview } = this.evaluateExpression(context, locals, value);

    const error = spelError ? <span className="error-message">{this.getErrorMessage(spelError)}</span> : null;

    return (
      <div className="flex-container-h baseline margin-between-lg">
        <div className="sm-label-right" style={{ minWidth: '120px' }}>
          {label} {Help}
        </div>
        <div className="flex-grow flex-container-v">
          <div className="flex-container-v">
            <input
              autoComplete="off"
              className="form-control"
              type="text"
              value={value}
              onChange={this.handleChange}
              placeholder={placeholder}
              required={required}
            />
            {spelPreview && (
              <div className="flex-container-h baseline margin-between-lg">
                <span className="no-grow">Preview:</span>{' '}
                {markdown ? <Markdown message={spelPreview} /> : <span>{spelPreview}</span>}
              </div>
            )}
          </div>
          {error}
          {spelError && this.renderError(spelError)}
        </div>
      </div>
    );
  }
}
