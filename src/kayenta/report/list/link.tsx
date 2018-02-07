import * as React from 'react';
import { ReactInjector } from '@spinnaker/core';

interface ILinkProps {
  targetState: string;
  stateParams: {[key: string]: any};
  linkText: string;
}

export const Link = ({ targetState, stateParams, linkText }: ILinkProps) => {
  const handleClick = () =>
    ReactInjector.$state.go(targetState, stateParams);

  return (
    <a
      className="clickable"
      onClick={handleClick}
    >
      {linkText}
    </a>
  );
};
