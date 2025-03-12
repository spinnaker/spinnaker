import React from 'react';

export interface IBreakStringProps {
  className?: string;
  children?: React.ReactNode;
}

export const BreakString = ({ className, children }: IBreakStringProps) => (
  <span className={className} style={{ wordBreak: 'break-all', overflowWrap: 'break-word', hyphens: 'none' }}>
    {children}
  </span>
);
