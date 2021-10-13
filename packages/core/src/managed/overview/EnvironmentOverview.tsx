import React from 'react';

import { Resource } from './Resource';
import { Artifact } from './artifact/Artifact';
import { BaseEnvironment } from '../environmentBaseElements/BaseEnvironment';
import type { ICollapsibleSectionProps } from '../../presentation';
import { CollapsibleSection } from '../../presentation';
import type { QueryEnvironment } from './types';

const sectionProps: Partial<ICollapsibleSectionProps> = {
  outerDivClassName: 'environment-section',
  headingClassName: 'environment-section-heading',
  bodyClassName: 'environment-section-body',
};

interface IEnvironmentProps {
  environment: QueryEnvironment;
}

export const EnvironmentOverview = ({ environment }: IEnvironmentProps) => {
  const state = environment.state;

  return (
    <BaseEnvironment
      name={environment.name}
      basedOn={environment.basedOn}
      gitMetadata={environment.gitMetadata}
      isPreview={environment.isPreview}
      isDeleting={environment.isDeleting}
    >
      <CollapsibleSection
        heading="Code deployments"
        {...sectionProps}
        defaultExpanded
        cacheKey={`${environment.name}-artifacts`}
      >
        {state.artifacts?.length ? (
          state.artifacts.map((artifact) => (
            <Artifact key={artifact.reference} artifact={artifact} isPreview={environment.isPreview} />
          ))
        ) : (
          <NoItemsMessage>No artifacts found</NoItemsMessage>
        )}
      </CollapsibleSection>
      <CollapsibleSection heading="Infrastructure" {...sectionProps} enableCaching={false}>
        {state.resources?.length ? (
          state.resources.map((resource) => (
            <Resource key={resource.id} resource={resource} environment={environment.name} />
          ))
        ) : (
          <NoItemsMessage>No resources found</NoItemsMessage>
        )}
      </CollapsibleSection>
    </BaseEnvironment>
  );
};

const NoItemsMessage: React.FC = ({ children }) => (
  <div className="environment-row-element">
    <div className="no-items-message">{children}</div>
  </div>
);
