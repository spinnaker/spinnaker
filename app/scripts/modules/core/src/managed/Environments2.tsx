import { UIView, useSref } from '@uirouter/react';
import React from 'react';

import { HorizontalTabs } from 'core/presentation/horizontalTabs/HorizontalTabs';

import { Routes } from './managed.states';

import './Environments2.less';
import './overview/baseStyles.less';

export const featureFlag = 'newMD_UI';

const tabsInternal: { [key in Routes]: string } = {
  overview: 'Overview',
  config: 'Configuration',
};

const tabs = Object.entries(tabsInternal).map(([key, title]) => ({ title, path: `.${key}` }));

// TODO: this is a temporary name until we remove the old view
export const Environments2 = () => {
  const { href } = useSref('home.applications.application.environments', { new_ui: '0' });
  return (
    <div className="vertical Environments2">
      <HorizontalTabs tabs={tabs} />
      <UIView />
      {/* Some padding at the bottom */}
      <div style={{ minHeight: 32, minWidth: 32 }} />
      <a
        href={href}
        onClick={() => {
          localStorage.removeItem(featureFlag);
        }}
        style={{ position: 'absolute', bottom: 4, right: 36 }}
      >
        Switch to old view
      </a>
    </div>
  );
};
