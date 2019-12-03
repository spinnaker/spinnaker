import React from 'react';

import { Application } from 'core/application';
import { IPipeline } from 'core/domain';
import { PageNavigator, PageSection } from 'core/presentation';
import { SETTINGS } from 'core/config/settings';
import { ExecutionOptionsPageContent } from './ExecutionOptionsPageContent';
import { ExpectedArtifactsPageContent } from './ExpectedArtifactsPageContent';
import { TriggersPageContent } from './TriggersPageContent';
import { ParametersPageContent } from './ParametersPageContent';
import { NotificationsPageContent } from './NotificationsPageContent';
import { DescriptionPageContent } from './DescriptionPageContent';

export interface ITriggersProps {
  application: Application;
  pipeline: IPipeline;
  fieldUpdated: () => void;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
  viewState: { revertCount: number };
}

export function Triggers(props: ITriggersProps) {
  const { pipeline, viewState } = props;

  function checkFeatureFlag(flag: string): boolean {
    return !!SETTINGS.feature[flag];
  }

  // KLUDGE: This value is used as a React key when rendering the Triggers.
  // Whenever the pipeline is reverted, this causes the Triggers to remount and reset formik state.
  const revertCountKLUDGE = viewState.revertCount;
  return (
    <PageNavigator scrollableContainer="[ui-view]">
      <PageSection pageKey="concurrent" label="Execution Options" visible={!pipeline.strategy}>
        <ExecutionOptionsPageContent {...props} />
      </PageSection>
      <PageSection
        pageKey="artifacts"
        label="Expected Artifacts"
        badge={pipeline.expectedArtifacts ? pipeline.expectedArtifacts.length.toString() : '0'}
        noWrapper={true}
        visible={!checkFeatureFlag('artifactsRewrite') && checkFeatureFlag('artifacts')}
      >
        <ExpectedArtifactsPageContent {...props} />
      </PageSection>
      <PageSection
        pageKey="triggers"
        label="Automated Triggers"
        badge={pipeline.triggers ? pipeline.triggers.length.toString() : '0'}
        noWrapper={true}
      >
        <TriggersPageContent {...props} key={revertCountKLUDGE} />
      </PageSection>
      <PageSection
        pageKey="parameters"
        label="Parameters"
        badge={pipeline.parameterConfig ? pipeline.parameterConfig.length.toString() : '0'}
        noWrapper={true}
      >
        <ParametersPageContent {...props} />
      </PageSection>
      <PageSection
        pageKey="notifications"
        label="Notifications"
        badge={pipeline.notifications ? pipeline.notifications.length.toString() : '0'}
        visible={!pipeline.strategy}
      >
        <NotificationsPageContent {...props} />
      </PageSection>
      <PageSection pageKey="description" label="Description" noWrapper={true}>
        <DescriptionPageContent {...props} />
      </PageSection>
    </PageNavigator>
  );
}
