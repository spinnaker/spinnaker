import React from 'react';

export interface IServerGroupNamePreviewProps {
  application: string;
  stack: string;
  details: string;
}

export class ServerGroupNamePreview extends React.Component<IServerGroupNamePreviewProps> {
  public render() {
    const application = this.props.application ? this.props.application : '';
    const stack = this.props.stack ? `-${this.props.stack}` : '';

    const details = this.props.details ? `-${this.props.details}` : '';
    return (
      <pre style={{ textAlign: 'center' }}>
        <p> Your server group will be in the cluster:</p>
        <p>
          <b>
            {application}
            {stack}
            {details}
          </b>
        </p>
      </pre>
    );
  }
}
