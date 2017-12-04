import * as React from 'react';
import { HelpField } from '@spinnaker/core';

import './titledSubsection.less';

export interface ITitledSubsectionProps {
  children: any;
  title: string;
  helpKey?: string;
}

export default function TitledSubsection({ title, children, helpKey }: ITitledSubsectionProps) {
  return (
    <section className="titled-subsection">
      <h5 className="heading-5">{title} {helpKey && <HelpField id={helpKey}/>}</h5>
      <hr/>
      {children}
    </section>
  );
}
