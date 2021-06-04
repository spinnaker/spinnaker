import classnames from 'classnames';
import { reverse } from 'lodash';
import React from 'react';
import { atom, useRecoilState } from 'recoil';

import { useElementDimensions } from 'core/presentation/hooks/useDimensions.hook';
import { logger } from 'core/utils';

const STORAGE_KEY = 'MD_environmentsDirection';

const DIRECTIONS = ['list', 'sideBySide'] as const;
type Direction = typeof DIRECTIONS[number];

const isDirection = (value: string | null): value is Direction => {
  return Boolean(value && DIRECTIONS.includes(value as Direction));
};

const storedDirection = localStorage.getItem(STORAGE_KEY);

const environmentsDirectionState = atom<Direction>({
  key: 'environmentsDisplay',
  default: isDirection(storedDirection) ? storedDirection : 'list',
});

// The goal of this hook is to store the value in an atom to be shared across the app but also update the local storage
const useEnvironmentDirection = () => {
  const [direction, setDirection] = useRecoilState(environmentsDirectionState);
  React.useLayoutEffect(() => {
    localStorage.setItem(STORAGE_KEY, direction);
  }, [direction]);

  return { direction, setDirection };
};

export const EnvironmentsDirectionController = () => {
  const { direction, setDirection } = useEnvironmentDirection();
  return (
    <button
      type="button"
      className="btn env-direction-btn"
      onClick={() => setDirection((state) => (state === 'list' ? 'sideBySide' : 'list'))}
    >
      {direction === 'list' ? 'Grid view' : 'List view'}
      <i className={classnames(direction === 'list' ? 'far fa-list-alt' : 'fas fa-columns', 'sp-margin-xs-left')} />
    </button>
  );
};

const MIN_WIDTH_PER_COLUMN = 500;

interface IEnvironmentsRenderProps {
  className?: string;
  children: React.ReactElement[];
}

export const EnvironmentsRender = ({ className, children }: IEnvironmentsRenderProps) => {
  const { direction } = useEnvironmentDirection();
  const ref = React.useRef(null);
  const { width } = useElementDimensions({ ref, isActive: direction === 'sideBySide' });
  let numEnvironments = 1;
  if (Array.isArray(children)) {
    numEnvironments = children.length;
  } else {
    logger.log({
      level: 'ERROR',
      error: new Error('Environments children should be an array'),
      action: 'Environments::Render',
    });
  }

  const numColumns = Math.min(Math.round(width / MIN_WIDTH_PER_COLUMN), numEnvironments);

  return (
    <div
      ref={ref}
      className={classnames(className, 'environments-list', { 'side-by-side': direction === 'sideBySide' })}
      style={direction === 'sideBySide' ? { gridTemplateColumns: `repeat(${numColumns}, 1fr)` } : undefined}
    >
      {direction === 'list' && children}
      {direction === 'sideBySide' && width > 0 ? reverse(React.Children.toArray(children)) : null}
    </div>
  );
};
