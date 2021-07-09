import classnames from 'classnames';
import { reverse } from 'lodash';
import React from 'react';
import { atom, useRecoilState } from 'recoil';

import { useDimensions } from '../../presentation/hooks/useDimensions.hook';
import { useLogEvent } from '../utils/logging';

const STORAGE_KEY = 'MD_environmentsDirection';

const DIRECTIONS = ['listView', 'gridView'] as const;
type Direction = typeof DIRECTIONS[number];

const isDirection = (value: string | null): value is Direction => {
  return Boolean(value && DIRECTIONS.includes(value as Direction));
};

const storedDirection = localStorage.getItem(STORAGE_KEY);

const environmentsDirectionState = atom<Direction>({
  key: 'environmentsDisplay',
  default: isDirection(storedDirection) ? storedDirection : 'gridView',
});

// The goal of this hook is to store the value in an atom to be shared across the app but also update the local storage
export const useEnvironmentDirectionState = () => {
  const [direction, setDirection] = useRecoilState(environmentsDirectionState);
  React.useEffect(() => {
    localStorage.setItem(STORAGE_KEY, direction);
  }, [direction]);

  return { direction, setDirection };
};

export const useIsGridView = () => {
  const { direction } = useEnvironmentDirectionState();
  return direction === 'gridView';
};

export const EnvironmentsDirectionController = () => {
  const { direction, setDirection } = useEnvironmentDirectionState();
  const logEvent = useLogEvent('environmentsDirection');

  React.useEffect(() => {
    logEvent({ action: direction });
  }, [direction]);

  return (
    <button
      type="button"
      className="btn env-direction-btn"
      onClick={() => setDirection((state) => (state === 'listView' ? 'gridView' : 'listView'))}
    >
      {direction === 'listView' ? 'Grid view' : 'List view'}
      <i className={classnames(direction === 'listView' ? 'fas fa-columns' : 'far fa-list-alt', 'sp-margin-xs-left')} />
    </button>
  );
};

const MIN_WIDTH_PER_COLUMN = 500;

interface IEnvironmentsRenderProps {
  className?: string;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export const useOrderedEnvironment = <T extends object>(
  ref: React.RefObject<HTMLDivElement>,
  environments: T[],
): { className?: string; style?: React.CSSProperties; environments: T[] } => {
  const { direction } = useEnvironmentDirectionState();
  const { width } = useDimensions(ref, { isActive: direction === 'gridView' });

  const numColumns = Math.min(Math.round(width / MIN_WIDTH_PER_COLUMN), environments.length);

  const orderedEnvironments = direction === 'gridView' ? (reverse(environments) as T[]) : environments;
  const style: React.CSSProperties =
    direction === 'gridView' ? { gridTemplateColumns: `repeat(${numColumns}, 1fr)` } : {};
  const className = direction === 'gridView' ? 'grid-view' : undefined;

  return { environments: orderedEnvironments, style, className };
};

export const EnvironmentsRender = React.forwardRef<HTMLDivElement, IEnvironmentsRenderProps>(
  ({ className, style, children }, ref) => {
    return (
      <div ref={ref} className={classnames(className, 'environments-list')} style={style}>
        {children}
      </div>
    );
  },
);
