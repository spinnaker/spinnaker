import React from 'react';
import { IResourceLinkProps } from './resourceRegistry';
import { useGenerateLink } from './useGetResourceLink.hook';

export const ResourceTitle = ({ props }: { props: IResourceLinkProps }) => {
  const { displayName } = props;
  const linkProps = useGenerateLink(props);

  return (
    <>
      {linkProps ? (
        <a
          href={linkProps.href}
          {...(linkProps.isExternal ? { target: '_blank', rel: 'noopener noreferrer' } : undefined)}
        >
          {displayName}
        </a>
      ) : (
        displayName
      )}
    </>
  );
};
