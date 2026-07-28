import { useCurrentStateAndParams, useRouter } from '@uirouter/react';
import React from 'react';

export interface ITrafficGuardHelperLinkProps {
  errorMessage: string;
}

export function TrafficGuardHelperLink({ errorMessage }: ITrafficGuardHelperLinkProps) {
  const { stateService } = useRouter();
  const { params: stateParams } = useCurrentStateAndParams();
  if (!errorMessage.includes('has traffic guards enabled')) {
    return null;
  }
  const { project, application } = stateParams;
  const nextState = `home.${project ? 'project' : 'applications'}.application.config`;
  const params = { project, application, section: 'traffic-guards' };
  const href = stateService.href(nextState, params);
  return (
    <p>
      <a href={href}>Configure Traffic Guards for this application</a>
    </p>
  );
}
