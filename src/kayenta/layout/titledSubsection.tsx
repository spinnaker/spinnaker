import * as React from 'react';

import './titledSubsection.less';

interface ITitledSubsectionProps {
  children: any;
  title: string;
}

export default function TitledSubsection({ title, children }: ITitledSubsectionProps) {
  return (
    <section className="titled-subsection">
      <h5 className="heading-5">{title}</h5>
      <hr/>
      {children}
    </section>
  );
}
