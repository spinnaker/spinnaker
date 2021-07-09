import { useSref } from '@uirouter/react';

import { IResourceLinkProps, resourceManager } from './resourceRegistry';

export const useGenerateLink = (props: IResourceLinkProps): { href: string; isExternal?: boolean } | undefined => {
  const externalLink = resourceManager.getExperimentalDisplayLink(props);
  const routingInfo = resourceManager.getNativeResourceRoutingInfo(props) ?? { state: '', params: {} };
  const routeProps = useSref(routingInfo.state, routingInfo.params);

  if (externalLink) {
    return { href: externalLink, isExternal: true };
  }

  if (routeProps.href) {
    return { href: routeProps.href };
  }
  return undefined;
};
