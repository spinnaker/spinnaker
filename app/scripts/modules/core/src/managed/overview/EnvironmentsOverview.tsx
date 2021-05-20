import React from 'react';

import { CollapsibleSection, ICollapsibleSectionProps, useApplicationContextSafe } from 'core/presentation';
import { Spinner } from 'core/widgets';

import { Resource } from './Resource';
import { Artifact } from './artifact/Artifact';
import { ManagementWarning } from '../config/ManagementWarning';
import { BaseEnvironment } from '../environmentBaseElements/BaseEnvironment';
import { useFetchApplicationQuery, useFetchResourceStatusQuery } from '../graphql/graphql-sdk';
import { QueryEnvironment } from './types';
import { OVERVIEW_VERSION_STATUSES } from './utils';
import { spinnerProps } from '../utils/defaults';

import './EnvironmentsOverview.less';

export const EnvironmentsOverview = () => {
  const app = useApplicationContextSafe();
  const { data, error, loading } = useFetchApplicationQuery({
    variables: { appName: app.name, statuses: OVERVIEW_VERSION_STATUSES },
  });

  if (loading && !data) {
    return <Spinner {...spinnerProps} message="Loading environments..." />;
  }

  if (error) {
    console.warn(error);
    return (
      <div style={{ width: '100%' }}>
        Failed to load environments data, please refresh and try again.
        <p>{error.message}</p>
      </div>
    );
  }

  return (
    <div className="EnvironmentsOverview">
      <ManagementWarning appName={app.name} />
      {data?.application?.environments.map((env) => (
        <EnvironmentOverview key={env.name} environment={env} appName={app.name} />
      ))}
    </div>
  );
};

const sectionProps: Partial<ICollapsibleSectionProps> = {
  outerDivClassName: 'environment-section',
  headingClassName: 'environment-section-heading',
  bodyClassName: 'environment-section-body',
};

interface IEnvironmentProps {
  appName: string;
  environment: QueryEnvironment;
}

const EnvironmentOverview = ({ appName, environment }: IEnvironmentProps) => {
  const { data } = useFetchResourceStatusQuery({ variables: { appName } });
  const resources = data?.application?.environments.find((env) => env.name === environment.name)?.state.resources;
  const hasResourcesWithIssues = resources?.some((resource) => resource.state?.status !== 'UP_TO_DATE');
  const state = environment.state;
  return (
    <BaseEnvironment title={environment.name}>
      <CollapsibleSection heading="Artifacts" {...sectionProps} defaultExpanded enableCaching={false}>
        {state.artifacts?.map((artifact) => (
          <Artifact key={artifact.reference} artifact={artifact} />
        ))}
      </CollapsibleSection>
      <CollapsibleSection
        heading="Resources"
        key={`resources-section-${Boolean(data)}`} // This is used remount the section for defaultExpanded to work
        {...sectionProps}
        enableCaching={false}
        defaultExpanded={hasResourcesWithIssues}
      >
        {state.resources?.map((resource) => (
          <Resource key={resource.id} resource={resource} environment={environment.name} />
        ))}
      </CollapsibleSection>
    </BaseEnvironment>
  );
};
