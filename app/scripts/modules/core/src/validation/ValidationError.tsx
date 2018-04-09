import * as React from 'react';

export class ValidationError extends React.Component<{ message: string }> {
  public render() {
    return (
      <div className="error-message">
        <span className="fa fa-exclamation-circle" /> {this.props.message}
      </div>
    );
  }
}
