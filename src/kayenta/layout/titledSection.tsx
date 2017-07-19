import * as React from 'react';

export interface ISectionProps {
  title: string;
  children?: JSX.Element[];
}

export default function TitledSection({ title, children }: ISectionProps) {
  return (
    <section>
      <h3>{title}</h3>
      {children}
    </section>
  );
}
