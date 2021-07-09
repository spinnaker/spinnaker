import { DateTime } from 'luxon';
import React from 'react';

import { IArtifactDetailProps } from './ArtifactDetail';
import { Button } from '../Button';
import { StatusCard } from '../StatusCard';
import { showUnpinArtifactModal } from './UnpinArtifactModal';
import { IPinned } from '../../domain';
import { Markdown } from '../../presentation';
import { useApplicationContext } from '../../presentation/hooks/useApplicationContext.hook';

interface PinnedCardProps extends Pick<IArtifactDetailProps, 'resourcesByEnvironment' | 'reference' | 'version'> {
  environmentName: string;
  pinned: IPinned;
}

export const PinnedCard: React.FC<PinnedCardProps> = ({
  version,
  environmentName,
  pinned,
  resourcesByEnvironment,
  reference,
}) => {
  const application = useApplicationContext();
  if (!application) return null;
  return (
    <StatusCard
      iconName="pin"
      appearance="warning"
      background={true}
      timestamp={DateTime.fromISO(pinned.at)}
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
