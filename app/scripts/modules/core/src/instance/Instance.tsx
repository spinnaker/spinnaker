import classNames from 'classnames';
import React from 'react';

import { IInstance } from '../domain';

import { Tooltip } from '../presentation';

export interface IInstanceProps {
  instance: IInstance;
  active: boolean;
  highlight: string;
  onInstanceClicked(instance: IInstance): void;
}

export const Instance = React.memo(function Instance(props: IInstanceProps) {
  const { instance, active, highlight, onInstanceClicked } = props;
  const { name, id, healthState } = instance;
  const [mouseOver, setMouseOver] = React.useState(false);

  const handleClick = (event: React.MouseEvent<any>) => {
    event.preventDefault();
    onInstanceClicked(instance);
  };

  const className = classNames({ highlighted: highlight === id, active: active });
  const anchor = (
    <a
      className={`instance health-status-${healthState} ${className}`}
      title={name || id}
      onMouseOver={() => setMouseOver(true)}
      onMouseOut={() => setMouseOver(false)}
      onClick={handleClick}
    />
  );

  // Perf optimization: do not render the Tooltip component unless we received an onMouseOver event
  return mouseOver ? <Tooltip value={name || id}>{anchor}</Tooltip> : anchor;
});
