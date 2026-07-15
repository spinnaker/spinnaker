import React from 'react';

import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';

import type { IAppengineServerGroup } from '../../domain';

export function AppengineServerGroupDetailsSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const appengineServerGroup = serverGroup as IAppengineServerGroup;

  return (
    <div className="content-section">
      <div className="content-section-heading">App Engine Details</div>
      <dl className="dl-horizontal dl-narrow">
        <dt>Account</dt>
        <dd>{appengineServerGroup.account}</dd>
        <dt>Region</dt>
        <dd>{appengineServerGroup.region}</dd>
        <dt>Status</dt>
        <dd>{appengineServerGroup.servingStatus || appengineServerGroup.status || 'Unknown'}</dd>
        <dt>Environment</dt>
        <dd>{appengineServerGroup.env || 'Unknown'}</dd>
        <dt>Scaling</dt>
        <dd>{appengineServerGroup.scalingPolicy?.type || 'Unknown'}</dd>
      </dl>
    </div>
  );
}
