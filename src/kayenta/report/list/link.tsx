import * as React from 'react';
import { ReactInjector } from '@spinnaker/core';

interface ILinkProps {
  targetState: string;
  stateParams: {[key: string]: any};
  children?: React.ReactNode;
}

export const Link = ({ targetState, stateParams, children }: ILinkProps) => {
  const handleClick = () =>
    ReactInjector.$state.go(targetState, stateParams);

  return (
    <a
      className="clickable"
      onClick={handleClick}
    >
      {children}
    </a>
  );
};
