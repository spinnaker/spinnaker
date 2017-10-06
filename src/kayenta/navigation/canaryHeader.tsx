import * as React from 'react';
import { UISref } from '@uirouter/react';

import { ReactInjector } from '@spinnaker/core';

import { Tab, Tabs } from '../layout/tabs';
import { ICanaryHeaderTabConfig } from './canaryTabs';

import './canaryHeader.less';

interface ICanaryHeaderProps {
  title: string;
  tabs: ICanaryHeaderTabConfig[];
}

/*
* Layout for top-level canary header.
* */
export const CanaryHeader = ({ title, tabs }: ICanaryHeaderProps) => {
  return (
    <nav className="horizontal">
      <Tabs className="flex-3">
        <CanaryTitle title={title}/>
      </Tabs>
      <Tabs className="flex-11">
        {tabs.filter(t => !t.hide).map(t => <CanaryTab key={t.title} tab={t}/>)}
      </Tabs>
    </nav>
  );
};

const CanaryTitle = ({ title }: { title: string }) => {
  return (
    <h1 className="heading-1">{title}</h1>
  );
};

const CanaryTab = ({ tab }: { tab: ICanaryHeaderTabConfig }) => {
  const isSelected = tab.activeStates.some(s => ReactInjector.$state.includes(s));
  return (
    <Tab selected={isSelected}>
      <UISref to={tab.sref}>
        <a>{tab.title}</a>
      </UISref>
    </Tab>
  );
};
