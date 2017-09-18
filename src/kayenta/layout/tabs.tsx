import * as React from 'react';

export interface ITabsProps {
  children: any;
}

export function Tabs({ children }: ITabsProps) {
  return (
    <ul className="tabs-basic list-unstyled">
      {children}
    </ul>
  );
}

export interface ITabProps {
  selected: boolean;
  children: JSX.Element | JSX.Element[];
}

export function Tab({ selected = false, children }: ITabProps) {
  return (
    <li className={selected ? 'selected' : ''}>
      {children}
    </li>
  );
}
