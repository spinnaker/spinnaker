import * as React from 'react';

import ConfigDetailHeader from './configDetailHeader';
import EditMetricModal from './editMetricModal';
import GroupTabs from './groupTabs';
import TitledSection from '../layout/titledSection';
import MetricList from './metricList';
import NameAndDescription from './nameAndDescription';
import Scoring from './scoring';

/*
 * Top-level config detail layout
 */
export default function ConfigDetail() {
  return (
    <section className="config-detail">
      <ConfigDetailHeader />
      <TitledSection title="Name and Description">
        <NameAndDescription />
      </TitledSection>
      <TitledSection title="Metrics">
        <GroupTabs />
        <MetricList />
        <EditMetricModal />
      </TitledSection>
      <TitledSection title="Scoring">
        <Scoring />
      </TitledSection>
    </section>
  );
}
