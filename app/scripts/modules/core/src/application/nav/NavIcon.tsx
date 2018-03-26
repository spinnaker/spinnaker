import * as React from 'react';

export interface INavIconProps {
  icon: string;
}

export const NavIcon = ({ icon }: INavIconProps) => {
  return icon ? <i className={`nav-item-icon fa fa-xs fa-fixed fa-${icon}`}/> : null;
};
