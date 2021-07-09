import classNames from 'classnames';
import { isEmpty, throttle } from 'lodash';
import * as React from 'react';

import { BuildDetailsScrollContainerContext } from './BuildDetailsScrollContainerContext';
import { CIReader } from '../CIReader';
import { ICiBuild } from '../domain';
import { Tooltip, useInterval } from '../../presentation';
import { Spinner } from '../../widgets';

interface ILogResult {
  logs: string[];
  state: 'loaded' | 'loading';
}
const INIT_LOG_RESULT: ILogResult = {
  logs: [],
  state: 'loading',
};

interface IBuildInfoLogsTabProps {
  build: ICiBuild;
}
export function BuildInfoLogsTab({ build }: IBuildInfoLogsTabProps) {
  const [logResult, setLogResult] = React.useState<ILogResult>(INIT_LOG_RESULT);
  const [autoScroll, setAutoScroll] = React.useState<boolean>(false);
  const [scrollTop, setScrollTop] = React.useState<number>(0);
  const logContainerEnd = React.useRef(null);
  const retrieveOutputLogs = (offset = -1) => {
    CIReader.getBuildOutput(build.id, offset).then((response) =>
      setLogResult((logResult) => ({
        logs: [...logResult.logs, response.log],
        state: build.isRunning ? 'loading' : 'loaded',
      })),
    );
  };
  const buildDetailsScrollContainer = React.useContext(BuildDetailsScrollContainerContext);

  React.useEffect(() => {
    setLogResult(INIT_LOG_RESULT);
    setAutoScroll(false);
    retrieveOutputLogs();
  }, [build.id]);

  useInterval(() => {
    if (build.isRunning) {
      retrieveOutputLogs(logResult.logs.length);
      if (autoScroll) {
        logContainerEnd.current?.scrollIntoView({ behavior: 'smooth' });
      }
    }
  }, 1000);

  React.useEffect(() => {
    const scrollContainer = buildDetailsScrollContainer || window;
    const scrollHandler = throttle(() => {
      if (scrollTop > scrollContainer.scrollTop) {
        setAutoScroll(false);
      }
      setScrollTop(scrollContainer.scrollTop);
    }, 300);

    scrollContainer.addEventListener('scroll', scrollHandler);
    return () => {
      scrollContainer.removeEventListener('scroll', scrollHandler);
    };
  }, [scrollTop, buildDetailsScrollContainer]);

  const scrollToBottomHandler = () => {
    if (autoScroll) {
      setAutoScroll(false);
    } else {
      setAutoScroll(true);
      logContainerEnd.current.scrollIntoView({ behavior: 'smooth' });
    }
  };

  if (isEmpty(logResult.logs) && logResult.state === 'loading') {
    return (
      <div className="build-details-loader flex-container-h center middle">
        <Spinner size="medium" />
      </div>
    );
  }

  return (
    <div className="row">
      <div className="col-md-12">
        <div className="text-right auto-scroll-header sticky-header">
          <Tooltip placement="top" value="Scroll to bottom">
            <a onClick={scrollToBottomHandler}>
              <span className={classNames('glyphicon glyphicon-circle-arrow-down', { enabled: autoScroll })} />
            </a>
          </Tooltip>
        </div>

        <div>
          <pre className="small">
            <p style={{ fontFamily: 'monospace' }}>
              <span />
              {logResult.logs.join('')}
            </p>
          </pre>
          <div ref={logContainerEnd} />
        </div>
      </div>
    </div>
  );
}
