import * as React from 'react';
import { parseSpelExpressions } from '../spel2js.templateParser';
import { BindAll } from 'lodash-decorators';
import { truncate } from 'lodash';

import { Markdown } from 'core/presentation/Markdown';

import { Modal } from 'react-bootstrap';

export interface IExpressionInputProps {
  placeholder?: string;
  context?: object;
  locals?: object;
  markdown?: boolean;
  Help?: JSX.Element;
  label: string;
  name: string;
  onChange: (event: React.ChangeEvent<any>) => void;
  value?: string;
  required?: boolean;
}

export interface IExpressionInputState {
  showContextModal: boolean;
  spelPreview: string;
  spelError: ISpelError;
  value: string;
}

export interface ISpelError {
  message: string;
  context: string;
  contextTruncated: string;
}

@BindAll()
export class ExpressionInput extends React.Component<IExpressionInputProps, IExpressionInputState> {
  public static defaultProps = { context: {}, locals: {} };

  constructor(props: IExpressionInputProps) {
    super(props);
    this.state = {
      showContextModal: false,
      spelPreview: null,
      spelError: null,
      value: props.value || '',
    };
  }

  public componentWillReceiveProps(nextProps: IExpressionInputProps): void {
    if (this.props.value !== nextProps.value) {
      this.validate(nextProps.value);
    }
  }

  private validate(value: string): void {
    if (!value) {
      return;
    }

    const { context, locals, onChange } = this.props;

    const stringify = (obj: any): string => {
      return obj === null ? 'null' : obj === undefined ? 'undefined' : JSON.stringify(obj, null, 2);
    };

    try {
      const exprs = parseSpelExpressions(value);
      const results = exprs.map(expr => expr.eval(context, locals));
      this.setState({ spelError: null, spelPreview: results.join(''), value });

      onChange({ target: { value } } as React.ChangeEvent<any>);
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

      this.setState({ spelError, spelPreview: null, value });
      onChange({ target: { value: '' } } as React.ChangeEvent<any>);
    }
  }

  public getErrorMessage(spelError: ISpelError): string {
    if (spelError) {
      return spelError.message + (spelError.contextTruncated ? ' -- ' + spelError.contextTruncated : '');
    }
    return null;
  }

  private hideContextModal(): void {
    this.setState({ showContextModal: false });
  }

  private showContextModal(): void {
    this.setState({ showContextModal: true });
  }

  private renderError(): JSX.Element {
    const { spelError } = this.state;

    return (
      <div>
        {spelError &&
          spelError.message && (
            <Modal show={this.state.showContextModal} onHide={this.hideContextModal}>
              <Modal.Header>
                <h3>{this.state.spelError.message}</h3>
              </Modal.Header>

              <Modal.Body>
                <pre>{this.state.spelError.context}</pre>
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

  private handleChange(event: React.ChangeEvent<HTMLInputElement>): void {
    const { value } = event.target;
    this.validate(value);
  }

  public render(): JSX.Element {
    const { Help, label, markdown, placeholder, required } = this.props;
    const { spelError, spelPreview, value } = this.state;

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
          {spelError && this.renderError()}
        </div>
      </div>
    );
  }
}
