import React from 'react';
import { DateTime } from 'luxon';

import { IPinned } from 'core/domain';

import { Markdown } from '../../presentation';
import { useApplicationContext } from '../../presentation/hooks/useApplicationContext.hook';
import { Button } from '../Button';
import { StatusCard } from '../StatusCard';
import { IArtifactDetailProps } from './ArtifactDetail';
import { showUnpinArtifactModal } from './UnpinArtifactModal';

interface PinnedCardProps extends Pick<IArtifactDetailProps, 'resourcesByEnvironment' | 'reference' | 'version'> {
  environmentName: string;
  pinned?: IPinned;
}

export const PinnedCard: React.FC<PinnedCardProps> = ({
  version,
  environmentName,
  pinned,
  resourcesByEnvironment,
  reference,
}) => {
  const application = useApplicationContext();
  return (
    <StatusCard
      iconName="pin"
      appearance="warning"
      background={true}
      timestamp={pinned?.at ? DateTime.fromISO(pinned.at) : null}
      title={
        <span className="sp-group-margin-xs-xaxis">
          <span>Pinned</span> <span className="text-regular">—</span>{' '}
          <span className="text-regular">by {pinned.by}</span>
        </span>
      }
      description={pinned.comment && <Markdown message={pinned.comment} tag="span" />}
      actions={
        <Button
          iconName="unpin"
          onClick={() =>
            showUnpinArtifactModal({
              application,
              reference,
              version,
              resourcesByEnvironment,
              environment: environmentName,
            }).then(({ status }) => status === 'CLOSED' && application.getDataSource('environments').refresh())
          }
        >
          Unpin
        </Button>
      }
    />
  );
};
