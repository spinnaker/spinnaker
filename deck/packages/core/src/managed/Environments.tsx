import { UIView, useCurrentStateAndParams } from '@uirouter/react';
import React from 'react';

import { EnvironmentsDirectionController } from './environmentBaseElements/EnvironmentsRender';
import type { Routes } from './managed.states';
import { HorizontalTabs } from '../presentation/horizontalTabs/HorizontalTabs';
import { setDebugMode } from './utils/debugMode';
import { useLogEvent } from './utils/logging';

import './Environments.less';
import './overview/baseStyles.less';

const tabsInternal: { [key in Routes]: string } = {
  overview: 'Overview',
  history: 'History',
  config: 'Configuration',
};

const tabs = Object.entries(tabsInternal).map(([key, title]) => ({ title, path: `.${key}` }));

export const Environments = () => {
  const logEvent = useLogEvent('EnvironmentsTab');
  const { state, params } = useCurrentStateAndParams();

  React.useEffect(() => {
    if (params.md_debug === 'enabled') {
      setDebugMode(true);
    } else if (params.md_debug === 'disabled') {
      setDebugMode(false);
    }
  }, [params]);

  return (
    <div className="vertical Environments">
      <HorizontalTabs
        tabs={tabs}
        rightElement={!state.name?.endsWith('.config') ? <EnvironmentsDirectionController /> : undefined}
        onClick={({ title, path }) => {
          logEvent({ action: `Open_${title}`, data: { path } });
        }}
      />
      <UIView />
      {/* Some padding at the bottom */}
      <div style={{ minHeight: 32, minWidth: 32 }} />
    </div>
  );
};
