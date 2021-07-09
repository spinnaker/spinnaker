import React from 'react';

import { IPipelineTemplatePlanError } from './PipelineTemplateReader';

export interface ITemplatePlanErrorsProps {
  errors: IPipelineTemplatePlanError[];
}

export class TemplatePlanErrors extends React.Component<ITemplatePlanErrorsProps> {
  public render() {
    if (this.props.errors && this.props.errors.length) {
      return (
        <div>
          <strong>
            Error
            {this.props.errors.length > 1 ? 's' : ''}:
          </strong>
          {this.props.errors.map((e) => this.buildErrorMessage(e))}
        </div>
      );
    } else {
      return (
        <div>
          <strong>Error:</strong> No message provided
        </div>
      );
    }
  }

  private buildErrorMessage(e: IPipelineTemplatePlanError, paddingLeft = 15): JSX.Element {
    return (
      <div style={{ paddingLeft: `${paddingLeft}px` }} key={this.buildErrorKey(e)}>
        {e.message && <div>Message: {e.message}</div>}
        {e.severity && <div>Severity: {e.severity}</div>}
        {e.location && <div>Location: {e.location}</div>}
        {e.cause && <div>Cause: {e.cause}</div>}
        {e.suggestion && <div>Suggestion: {e.suggestion}</div>}
        {e.details && (
          <div>
            {' '}
            Details:
            <ul>
              {Object.keys(e.details).map((key) => (
                <li key={key}>
                  {key}: {e.details[key]}
                </li>
              ))}
            </ul>
          </div>
        )}
        {e.nestedErrors && (
          <div>
            {' '}
            Nested Errors:
            {e.nestedErrors.map((nestedError) => this.buildErrorMessage(nestedError, paddingLeft + 15))}
          </div>
        )}
      </div>
    );
  }

  private buildErrorKey(e: IPipelineTemplatePlanError): string {
    return `${e.message}:${e.severity}:${e.location}:${e.cause}`;
  }
}
