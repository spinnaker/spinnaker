import * as React from 'react';

import './titledSection.less';

export interface ISectionProps {
  title: string;
  children?: any;
}

// TODO: implemented the mocks using a modified open pod, but the styleguide should add something more directly appropriate
export default function TitledSection({ title, children }: ISectionProps) {
  return (
    <section className="pod open titled-section">
      <div className="header horizontal middle">
        <div className="flex-1 heading-2 uppercase">{title}</div>
      </div>
      <div className="contents">
        {children}
      </div>
    </section>
  );
}
