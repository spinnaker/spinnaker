import * as React from 'react';
import { UISref } from '@uirouter/react';
import { connect } from 'react-redux';

import { Tab, Tabs } from '../layout/tabs';
import { ICanaryHeaderTabConfig } from './canaryTabs';
import { ICanaryState } from '../reducers';

import './canaryHeader.less';

export interface ICanaryHeaderOwnProps {
  title: string;
  tabs: ICanaryHeaderTabConfig[];
}

interface ICanaryHeaderStateProps {
  activeTab: string;
}

/*
* Layout for top-level canary header.
* */
const CanaryHeader = ({ title, tabs, activeTab }: ICanaryHeaderOwnProps & ICanaryHeaderStateProps) => {
  return (
    <nav className="horizontal">
      <Tabs className="flex-2">
        <CanaryTitle title={title}/>
      </Tabs>
      <Tabs className="flex-9">
        {tabs.filter(t => !t.hide).map(t => <CanaryTab key={t.title} activeTab={activeTab} tab={t}/>)}
      </Tabs>
    </nav>
  );
};

const CanaryTitle = ({ title }: { title: string }) => {
  return (
    <h1 className="heading-1">{title}</h1>
  );
};

const CanaryTab = ({ tab, activeTab }: { tab: ICanaryHeaderTabConfig, activeTab: string }) => {
  const isSelected = tab.title === activeTab;
  return (
    <Tab selected={isSelected}>
      <UISref to={tab.sref}>
        <a>{tab.title}</a>
      </UISref>
    </Tab>
  );
};

const mapStateToProps = (state: ICanaryState, ownProps: ICanaryHeaderOwnProps): ICanaryHeaderStateProps & ICanaryHeaderOwnProps => ({
  activeTab: state.app.activeTab,
  ...ownProps
});

export default connect(mapStateToProps)(CanaryHeader);
