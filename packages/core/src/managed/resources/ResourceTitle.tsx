import React from 'react';

import { IResourceLinkProps } from './resourceRegistry';
import { useGenerateLink } from './useGetResourceLink.hook';
import { useLogEvent } from '../utils/logging';

export const ResourceTitle = ({ props }: { props: IResourceLinkProps }) => {
  const { displayName } = props;
  const linkProps = useGenerateLink(props);
  const logEvent = useLogEvent('Resource');

  return (
    <>
      {linkProps ? (
        <a
          href={linkProps.href}
          {...(linkProps.isExternal ? { target: '_blank', rel: 'noopener noreferrer' } : undefined)}
          onClick={() => {
            logEvent({ action: 'OpenCommit', data: { kind: props.kind, account: props.account } });
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
