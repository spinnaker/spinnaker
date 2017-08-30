import * as React from 'react';

export interface ISectionProps {
  title: string;
  children?: any;
}

// TODO: implemented the mocks using a modified open pod, but the styleguide should add something more directly appropriate
export default function TitledSection({ title, children }: ISectionProps) {
  return (
    <section className="pod open" style={{ marginBottom: '1em' }}>
      <div className="header horizontal middle">
        <div className="flex-1 heading-2 uppercase">{title}</div>
      </div>
      <div className="contents" style={{ backgroundColor: 'transparent' }}>
        {children}
      </div>
    </section>
  );
}
