import * as React from 'react';

export const STYLEGUIDE_CLASS = 'styleguide';

export interface IStyleguideProps {
  children: JSX.Element | JSX.Element[];
}

export default function Styleguide({children}: IStyleguideProps) {
  return (
    <div className={STYLEGUIDE_CLASS}>
      {children}
    </div>
  );
}
