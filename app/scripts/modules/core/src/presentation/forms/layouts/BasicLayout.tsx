import * as React from 'react';
import { IFieldLayoutProps } from '../interface';

export class BasicLayout extends React.Component<IFieldLayoutProps> {
  public render() {
    const { label, help, input, actions, error, warning, preview } = this.props;

    const renderError = (message: string | JSX.Element) =>
      typeof message === 'string' ? <div className="warn-message">{message}</div> : message;
    const renderWarning = (message: string | JSX.Element) =>
      typeof message === 'string' ? <div className="error-message">{message}</div> : message;
    const renderPreview = (message: string | JSX.Element) =>
      typeof message === 'string' ? <div>{message}</div> : message;

    const validation = renderError(error) || renderWarning(warning) || renderPreview(preview);

    return (
      <div className="flex-container-h baseline margin-between-lg">
        <div className="sm-label-right" style={{ minWidth: '120px' }}>
          {label} {help}
        </div>

        <div className="flex-grow">
          <div className="flex-container-v">
            <div className="flex-container-h baseline margin-between-lg">
              {input} {actions}
            </div>

            {validation}
          </div>
        </div>
      </div>
    );
  }
}
