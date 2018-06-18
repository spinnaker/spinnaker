import * as React from 'react';

export interface IKubernetesManifestCondition {
  status: string;
  type: string;
  lastTransitionTime: string;
  message: string;
}

export interface IKubernetesManifestConditionProps {
  condition: IKubernetesManifestCondition;
}

export class ManifestCondition extends React.Component<IKubernetesManifestConditionProps> {
  public render() {
    const { condition } = this.props;
    return [
      <span key="properties">
        {condition.status === 'True' && <span style={{ marginRight: '3px' }} className="glyphicon glyphicon-Normal" />}
        {condition.status === 'False' && <span style={{ marginRight: '3px' }} className="glyphicon glyphicon-Warn" />}
        {condition.status === 'Unknown' && <span style={{ marginRight: '3px' }}>?</span>}
        <b style={{ marginRight: '3px' }}>{condition.type}</b>
        <i>{condition.lastTransitionTime}</i>
      </span>,
      <div key="message">{condition.message}</div>,
    ];
  }
}
