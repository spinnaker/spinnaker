import { CopyToClipboard } from 'core/utils';
import { ReactInjector } from 'core/reactShims';
import * as React from 'react';
import * as ReactGA from 'react-ga';

export interface IExecutionPermalinkProps {
  standalone: boolean;
}

export const ExecutionPermalink = ({ standalone }: IExecutionPermalinkProps) => {
  const [url, setUrl] = React.useState(location.href);

  React.useEffect(() => {
    const subscription = ReactInjector.stateEvents.locationChangeSuccess.subscribe(() => {
      const newUrl = location.href;
      if (url !== newUrl) {
        if (!standalone) {
          setUrl(newUrl);
        } else {
          setUrl(newUrl.replace('/executions', '/executions/details'));
        }
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
