import React, { useState } from 'react';
import { animated, useSpring } from 'react-spring';

import './Pill.less';

interface IPillProps {
  text: string;
  bgColor?: string;
  textColor?: string;
}

export function Pill({ text, bgColor = '#666666', textColor = '#ffffff' }: IPillProps) {
  return (
    <div className="Pill text-bold" style={{ backgroundColor: bgColor, color: textColor }}>
      {text}
    </div>
  );
}

export const AnimatingPill = (props: IPillProps) => {
  const [reverseAnimation, setReverseAnimation] = useState<boolean>(false);
  const AnimatedPill = animated(Pill);

  const { opacity } = useSpring<{ opacity: number }>({
    config: {
      mass: 16,
      tension: 390,
      friction: 130,
    },
    from: { opacity: 0.5 },
    opacity: 1,
    reset: true,
    reverse: reverseAnimation,
    onRest: () => setReverseAnimation(!reverseAnimation),
  });

  return <AnimatedPill {...props} bgColor={opacity.interpolate(o => `rgba(97, 185, 255, ${o})`)} />;
};
