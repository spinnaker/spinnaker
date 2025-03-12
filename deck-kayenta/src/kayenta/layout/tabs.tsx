import classNames from 'classnames';
import * as React from 'react';

export interface ITabsProps {
  children: any;
  className?: string;
  style?: React.CSSProperties;
}

export function Tabs({ children, className, style }: ITabsProps) {
  return (
    <ul className={classNames('tabs-basic', 'list-unstyled', className)} style={style}>
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
  return <li className={classNames(selected ? 'selected' : '', className)}>{children}</li>;
}
