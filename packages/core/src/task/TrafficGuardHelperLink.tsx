import React from 'react';

import { ReactInjector } from '../reactShims';

export interface ITrafficGuardHelperLinkProps {
  errorMessage: string;
}

export class TrafficGuardHelperLink extends React.Component<ITrafficGuardHelperLinkProps> {
  public render() {
    const { errorMessage } = this.props;
    if (!errorMessage.includes('has traffic guards enabled')) {
      return null;
    }
    const { $state, $stateParams } = ReactInjector;
    const { project, application } = $stateParams;
    const nextState = `home.${project ? 'project' : 'applications'}.application.config`;
    const params = { project, application, section: 'traffic-guards' };
    const href = $state.href(nextState, params);
    return (
      <p>
        <a href={href}>Configure Traffic Guards for this application</a>
      </p>
    );
  }
}
