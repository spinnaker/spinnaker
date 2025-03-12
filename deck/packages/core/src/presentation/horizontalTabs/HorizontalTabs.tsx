import { useSrefActive } from '@uirouter/react';
import classnames from 'classnames';
import React from 'react';
import './HorizontalTabs.less';

interface ITabProps {
  title: string;
  path: string;
}
export interface IHorizontalTabsProps {
  tabs: ITabProps[];
  className?: string;
  onClick?: (props: ITabProps) => void;
  rightElement?: React.ReactElement;
}

export const HorizontalTabs = ({ tabs, className, onClick, rightElement }: IHorizontalTabsProps) => {
  return (
    <div className={classnames(className, 'HorizontalTabs')}>
      {tabs.map((tab) => (
        <TabTitle key={tab.path} data={tab} onClick={onClick} />
      ))}
      {rightElement && <div className="right-element">{rightElement}</div>}
    </div>
  );
};

interface ITabTitleProps {
  data: ITabProps;
  onClick?: (props: ITabProps) => void;
}

const TabTitle = ({ data, onClick }: ITabTitleProps) => {
  const sRefProps = useSrefActive(data.path, {}, 'selected-tab');
  return (
    <a
      href={sRefProps.href}
      className={classnames('tab', sRefProps.className)}
      onClick={(e) => {
        sRefProps.onClick(e);
        onClick?.(data);
      }}
    >
      {data.title}
    </a>
  );
};
