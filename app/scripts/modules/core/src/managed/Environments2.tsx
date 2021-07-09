import { UIView, useCurrentStateAndParams, useSref } from '@uirouter/react';
import React from 'react';

import { EnvironmentsDirectionController } from './environmentBaseElements/EnvironmentsRender';
import { Routes } from './managed.states';
import { HorizontalTabs } from '../presentation/horizontalTabs/HorizontalTabs';
import { setDebugMode } from './utils/debugMode';
import { useLogEvent } from './utils/logging';

import './Environments2.less';
import './overview/baseStyles.less';

export const uiFeatureFlag = {
  key: 'MD_old_ui',
  value: '1',
};

export const getIsNewUI = () => {
  return localStorage.getItem(uiFeatureFlag.key) !== uiFeatureFlag.value;
};

export const toggleIsNewUI = () => {
  const isNew = getIsNewUI();
  if (isNew) {
    localStorage.setItem(uiFeatureFlag.key, uiFeatureFlag.value);
  } else {
    localStorage.removeItem(uiFeatureFlag.key);
  }
};

export const UISwitcher = () => {
  const { state, params } = useCurrentStateAndParams();
  const isNewUI = getIsNewUI();
  const logEvent = useLogEvent('uiSwitcher');

  const newUIstate =
    'home.applications.application.environments' +
    (state.name?.endsWith('.artifactVersion') ? '.history' : '.overview');

  const { href, onClick } = useSref(isNewUI ? 'home.applications.application.environments' : newUIstate || '', params, {
    reload: true,
  });
  return (
    <a
      href={href}
      onClick={(e) => {
        toggleIsNewUI();
        logEvent({ action: isNewUI ? 'SwitchToOld' : 'SwitchToNew' });
        onClick(e);
      }}
      className="ui-switcher"
    >
      {isNewUI ? 'Switch to old view' : 'Try out our new UI'}
    </a>
  );
};

const tabsInternal: { [key in Routes]: string } = {
  overview: 'Overview',
  history: 'History',
  config: 'Configuration',
};

const tabs = Object.entries(tabsInternal).map(([key, title]) => ({ title, path: `.${key}` }));

// TODO: this is a temporary name until we remove the old view
export const Environments2 = () => {
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
    <div className="vertical Environments2">
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
