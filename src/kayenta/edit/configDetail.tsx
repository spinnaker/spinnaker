import * as React from 'react';

import ConfigDetailHeader from './configDetailHeader';
import GroupTabs from './groupTabs';
import MetricList from './metricList';
import EditMetricModal from './editMetricModal';
import NameAndDescription from './nameAndDescription'
import TitledSection from '../layout/titledSection';

/*
 * Top-level config detail layout
 */
export default function ConfigDetail() {
  return (
    <section>
      <ConfigDetailHeader/>
      <TitledSection title="Name and Description">
        <NameAndDescription/>
      </TitledSection>
      <TitledSection title="Metrics">
        <GroupTabs/>
        <MetricList/>
        <EditMetricModal/>
      </TitledSection>
    </section>
  );
}
