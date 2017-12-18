import * as React from 'react';

import ConfigDetailHeader from './configDetailHeader';
import GroupTabs from './groupTabs';
import MetricList from './metricList';
import EditMetricModal from './editMetricModal';
import NameAndDescription from './nameAndDescription'
import TitledSection from '../layout/titledSection';
import Scoring from './scoring';
import Templates from './templates/templates';
import { CanarySettings } from 'kayenta/canary.settings';

/*
 * Top-level config detail layout
 */
export default function ConfigDetail() {
  return (
    <section className="config-detail">
      <ConfigDetailHeader/>
      <TitledSection title="Name and Description">
        <NameAndDescription/>
      </TitledSection>
      <TitledSection title="Metrics">
        <GroupTabs/>
        <MetricList/>
        <EditMetricModal/>
      </TitledSection>
      <TitledSection title="Scoring">
        <Scoring/>
      </TitledSection>
      {CanarySettings.templatesEnabled && (
        <TitledSection title="Templates">
          <Templates/>
        </TitledSection>
      )}
    </section>
  );
}
