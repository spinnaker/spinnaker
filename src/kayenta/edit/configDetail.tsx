import * as React from 'react';

import ConfigDetailHeader from './configDetailHeader';
import GroupTabs from './groupTabs';
import MetricList from './metricList';
import TitledSection from '../layout/titledSection';

/*
 * Top-level config detail layout
 */
export default function ConfigDetail() {
  return (
    <section>
      <ConfigDetailHeader/>
      <TitledSection title="Metrics">
        <GroupTabs/>
        <MetricList/>
      </TitledSection>
    </section>
  );
}
