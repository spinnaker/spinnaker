import React from 'react';

export interface ISingleLineStringProps {
  className?: string;
  children?: React.ReactNode;
}

export const SingleLineString = ({ className, children }: ISingleLineStringProps) => (
  <span className={className} style={{ whiteSpace: 'nowrap' }}>
    {children}
  </span>
);
