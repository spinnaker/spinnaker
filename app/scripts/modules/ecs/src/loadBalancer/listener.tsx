import React from 'react';

import { IEcsListener } from '../domain/IEcsLoadBalancer';

export interface IEcsListenerProps {
  listener: IEcsListener;
}

export class EcsListener extends React.Component<IEcsListenerProps> {
  constructor(props: IEcsListenerProps) {
    super(props);
  }

  private getTargetGroupNameFromArn(arn: string): string {
    const parts = arn.split('/');
    if (parts.length < 0) {
      return arn;
    }
    return parts[1];
  }

  public render(): React.ReactElement<EcsListener> {
    const { port, protocol, defaultActions } = this.props.listener;

    // TODO: retrieve and display 'rules' in addition to default actions
    return (
      <div>
        <h4>
          {protocol}:{port}
        </h4>
        <div style={{ paddingLeft: 10 }}>
          <i>default actions</i>
          <ul>
            {defaultActions.map((action, i) => {
              if (action.type == 'forward') {
                return (
                  <li key={i}>
                    &rarr;
                    <i className="fa fa-fw fa-crosshairs icon" aria-hidden="true"></i>
                    <span>
                      {action.targetGroupArn ||
                        action.forwardConfig.targetGroups.map((tg) => {
                          return this.getTargetGroupNameFromArn(tg.targetGroupArn);
                        })}
                    </span>
                  </li>
                );
              } else {
                return (
                  <li key={i}>
                    {action.type}:{' '}
                    {action.redirectConfig.statusCode ||
                      action.fixedResponseConfig.statusCode ||
                      action.authenticateOidcConfig.clientId ||
                      action.authenticateCognitoActionConfig.userPoolDomain}
                  </li>
                );
              }
            })}
          </ul>
        </div>
      </div>
    );
  }
}
