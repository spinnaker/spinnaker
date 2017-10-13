import * as React from 'react';
import * as classNames from 'classnames';

export interface ITabsProps {
  children: any;
  className?: string;
}

export function Tabs({ children, className }: ITabsProps) {
  return (
    <ul className={classNames('tabs-basic', 'list-unstyled', className)}>
      {children}
    </ul>
  );
}

export interface ITabProps {
  selected: boolean;
  children: JSX.Element | JSX.Element[];
  className?: string;
}

export function Tab({ selected = false, children, className }: ITabProps) {
  return (
    <li className={classNames(selected ? 'selected' : '', className)}>
      {children}
    </li>
  );
}
