import React from 'react';

import { IManagedArtifactVersion, IManagedResourceSummary } from '../domain';
import { useEventListener } from '../presentation';

import { ArtifactDetailHeader } from './ArtifactDetailHeader';
import { ManagedResourceObject } from './ManagedResourceObject';

import './ArtifactDetail.less';

function shouldDisplayResource(resource: IManagedResourceSummary) {
  //TODO: naively filter on presence of moniker but how should we really decide what to display?
  return !!resource.moniker;
}

export interface IArtifactDetailProps {
  name: string;
  version: IManagedArtifactVersion;
  resourcesByEnvironment: { [environment: string]: IManagedResourceSummary[] };
  onRequestClose: () => any;
}

export const ArtifactDetail = ({
  name,
  version: { version, environments },
  resourcesByEnvironment,
  onRequestClose,
}: IArtifactDetailProps) => {
  const keydownCallback = ({ keyCode }: KeyboardEvent) => {
    if (keyCode === 27 /* esc */) {
      onRequestClose();
    }
  };
  useEventListener(document, 'keydown', keydownCallback);

  return (
    <>
      <ArtifactDetailHeader name={name} version={version} onRequestClose={onRequestClose} />

      <div className="ArtifactDetail">
        <div className="flex-container-h">
          {/* a short summary with actions/buttons will live here */}
          <div className="detail-section-right">{/* artifact metadata will live here */}</div>
        </div>
        {environments.map(({ name }) => (
          <div key={name}>
            <h3>{name.toUpperCase()}</h3>
            {resourcesByEnvironment[name].filter(shouldDisplayResource).map(resource => (
              <ManagedResourceObject key={resource.id} resource={resource} />
            ))}
          </div>
        ))}
      </div>
    </>
  );
};
