import React, { memo } from 'react';
import { useTransition, animated } from 'react-spring';

import { Icon, IconNames } from '../presentation';

import './StatusBubble.less';

export interface IStatusBubbleProps {
  iconName: IconNames;
  appearance: 'inactive' | 'neutral' | 'info' | 'progress' | 'success' | 'warning' | 'error';
  size: 'extraSmall' | 'small' | 'medium' | 'large' | 'extraLarge';
}

const paddingBySize = {
  extraSmall: 'xs',
  small: 's',
  medium: 's',
  large: 's',
  extraLarge: 'm',
} as const;

const inStyles = {
  opacity: 1,
  transform: 'scale(1.0, 1.0)',
};

const outStyles = {
  opacity: 0,
  transform: 'scale(0.8, 0.8)',
};

const transitionConfig = {
  from: outStyles,
  enter: inStyles,
  leave: outStyles,
  config: { mass: 1, tension: 400, friction: 30 },
};

export const StatusBubble = memo(({ iconName, appearance, size }: IStatusBubbleProps) => {
  const transitions = useTransition(iconName, null, transitionConfig);

  return (
    <div className={`StatusBubble status-bubble-${appearance} sp-padding-${paddingBySize[size]}`}>
      {transitions.map(({ item, key, props }) => (
        <animated.div key={key} className="status-bubble-icon-container flex-container-h center middle" style={props}>
          <Icon name={item} appearance="light" size={size} />
        </animated.div>
      ))}
    </div>
  );
});
