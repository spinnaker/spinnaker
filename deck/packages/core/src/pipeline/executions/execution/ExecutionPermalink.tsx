import React from 'react';
import { AngularServices } from '../../../angular/services';

import { CopyToClipboard, logger } from '../../../utils';

export interface IExecutionPermalinkProps {
  standalone: boolean;
}

export const ExecutionPermalink = ({ standalone }: IExecutionPermalinkProps) => {
  const asPermalink = (link: string) => (standalone ? link : link.replace('/executions', '/executions/details'));

  const [url, setUrl] = React.useState(asPermalink(location.href));

  React.useEffect(() => {
    const subscription = AngularServices.stateEvents.locationChangeSuccess.subscribe((newUrl) => {
      if (url !== newUrl) {
        setUrl(asPermalink(newUrl));
      }
    });
    return () => subscription.unsubscribe();
  }, []);

  const handlePermalinkClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Permalink clicked' });
  };

  return (
    <>
      <a onClick={handlePermalinkClick} href={url}>
        Permalink
      </a>
      <CopyToClipboard text={url} toolTip="Copy permalink to clipboard" />
    </>
  );
};
