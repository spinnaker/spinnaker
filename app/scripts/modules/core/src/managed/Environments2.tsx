import { UIView, useCurrentStateAndParams, useSref } from '@uirouter/react';
import React from 'react';

import { HorizontalTabs } from 'core/presentation/horizontalTabs/HorizontalTabs';

import { Routes } from './managed.states';
import { useLogEvent } from './utils/logging';

import './Environments2.less';
import './overview/baseStyles.less';

export const uiFeatureFlag = {
  key: 'MD_new_ui',
  value: '1',
};

export const getIsNewUI = () => {
  return localStorage.getItem(uiFeatureFlag.key) === uiFeatureFlag.value;
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
        if (isNewUI) {
          localStorage.removeItem(uiFeatureFlag.key);
        } else {
          localStorage.setItem(uiFeatureFlag.key, uiFeatureFlag.value);
        }
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

  return (
    <div className="vertical Environments2">
      <HorizontalTabs
        tabs={tabs}
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
