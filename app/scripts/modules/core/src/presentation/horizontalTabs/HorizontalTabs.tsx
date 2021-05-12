import { useSrefActive } from '@uirouter/react';
import classnames from 'classnames';
import React from 'react';
import './HorizontalTabs.less';

export interface IHorizontalTabsProps {
  tabs: Array<{ title: string; path: string }>;
  className?: string;
  style?: React.CSSProperties;
}

export const HorizontalTabs = ({ tabs, className, style }: IHorizontalTabsProps) => {
  return (
    <div className={classnames(className, 'HorizontalTabs')} style={style}>
      {tabs.map((tab) => (
        <TabTitle key={tab.path} title={tab.title} path={tab.path} />
      ))}
    </div>
  );
};

interface TabTitleProps {
  title: string;
  path: string;
}

const TabTitle = ({ title, path }: TabTitleProps) => {
  const { href, className } = useSrefActive(path, {}, 'selected-tab');
  return (
    <a href={href} className={classnames('tab', className)}>
      {title}
    </a>
  );
};
