import React from 'react';

import type { QueryResource } from '../overview/types';
import { useGenerateLink } from './useGetResourceLink.hook';
import { useLogEvent } from '../utils/logging';

export const ResourceTitle = ({ resource }: { resource: QueryResource }) => {
  const account = resource.location?.account;
  const { displayName, kind } = resource;
  const linkProps = useGenerateLink({
    kind: kind,
    displayName,
    account,
    detail: resource.moniker?.detail,
    stack: resource.moniker?.stack,
  });
  const logEvent = useLogEvent('Resource');

  return (
    <>
      {linkProps ? (
        <a
          href={linkProps.href}
          {...(linkProps.isExternal ? { target: '_blank', rel: 'noopener noreferrer' } : undefined)}
          onClick={() => {
            logEvent({ action: 'OpenCommit', data: { kind, account } });
          }}
        >
          {displayName}
        </a>
      ) : (
        displayName
      )}
    </>
  );
};
