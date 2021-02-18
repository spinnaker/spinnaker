import { ReactInjector } from 'core/reactShims';
import { CopyToClipboard } from 'core/utils';
import React from 'react';
import ReactGA from 'react-ga';

export interface IExecutionPermalinkProps {
  standalone: boolean;
}

export const ExecutionPermalink = ({ standalone }: IExecutionPermalinkProps) => {
  const asPermalink = (link: string) => (standalone ? link : link.replace('/executions', '/executions/details'));

  const [url, setUrl] = React.useState(asPermalink(location.href));

  React.useEffect(() => {
    const subscription = ReactInjector.stateEvents.locationChangeSuccess.subscribe((newUrl) => {
      if (url !== newUrl) {
        setUrl(asPermalink(newUrl));
      }
    });
    return () => subscription.unsubscribe();
  }, []);

  const handlePermalinkClick = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Permalink clicked' });
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
