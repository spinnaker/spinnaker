import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import type { IStage } from '@spinnaker/core';
import { AccountService } from '@spinnaker/core';

import { FindArtifactsFromResourceConfig } from './findArtifactsFromResource/FindArtifactsFromResourceConfig';
import { ManifestKindSearchService } from '../../manifest/ManifestKindSearch';
import { PatchManifestStageConfig } from './patchManifest/PatchManifestStageConfig';
import { ScaleManifestStageConfig } from './scaleManifest/ScaleManifestConfig';
import { UndoRolloutManifestConfig } from './undoRolloutManifest/UndoRolloutManifestConfig';
import { getFormGroupByLabel } from '../../../../core/src/utils/testUtils/rtl';
import { setupUser } from '../../../../core/src/utils/testUtils/userEvent';

// These children fetch artifact accounts or need a real editor; they don't touch the manifest selector.
vi.mock('@spinnaker/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@spinnaker/core')>();
  return { ...actual, StageArtifactSelectorDelegate: () => null, YamlEditor: () => null };
});
vi.mock('./deployManifest/ManifestBindArtifactsSelector', () => ({ ManifestBindArtifactsSelector: () => null }));

const account = {
  name: 'my-account',
  namespaces: ['team-a', 'team-b'],
  spinnakerKindMap: { deployment: 'serverGroupManagers' },
};

// Applies updateStage to the shared stage object the way PipelineConfigPage does
function StageEditor({ component: StageConfig, stage }: { component: React.ComponentType<any>; stage: IStage }) {
  const [, setRevision] = React.useState(0);
  const stageFieldUpdated = () => setRevision((revision) => revision + 1);
  const updateStage = (changes: Partial<IStage>) => {
    Object.entries(changes).forEach(([key, value]) => {
      if (value !== undefined || key in stage) {
        stage[key] = value;
      }
    });
    stageFieldUpdated();
  };
  return (
    <StageConfig
      application={{ name: 'app', getDataSource: () => ({ data: [] }) }}
      pipeline={{ stages: [stage] }}
      stage={stage}
      stageFieldUpdated={stageFieldUpdated}
      updateStage={updateStage}
      updateStageField={updateStage}
    />
  );
}

describe('manifest selector stages', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockResolvedValue([account] as any);
    vi.spyOn(ManifestKindSearchService, 'search').mockResolvedValue([{ name: 'deployment my-deployment' }] as any);
  });

  const renderStage = async (component: React.ComponentType<any>, type: string) => {
    const stage = { refId: '1', type, account: 'my-account', cloudProvider: 'kubernetes' } as IStage;
    render(<StageEditor component={component} stage={stage} />);
    await waitFor(() => expect(within(getFormGroupByLabel('Namespace')).getByRole('combobox')).toBeInTheDocument());
    await waitFor(() => expect(AccountService.getAllAccountDetailsForProvider).toHaveBeenCalled());
    return stage;
  };

  const chooseNamespace = async (namespace: string) => {
    const user = setupUser();
    const field = getFormGroupByLabel('Namespace');
    await user.click(within(field).getByRole('combobox'));
    await user.click(await within(field).findByText(namespace, { selector: '.Select-option' }));
  };

  describe.each([
    ['Patch (Manifest)', PatchManifestStageConfig, 'patchManifest'],
    ['Undo Rollout (Manifest)', UndoRolloutManifestConfig, 'undoRolloutManifest'],
    ['Scale (Manifest)', ScaleManifestStageConfig, 'scaleManifest'],
    ['Find Artifacts From Resource (Manifest)', FindArtifactsFromResourceConfig, 'findArtifactsFromResource'],
  ])('%s', (_label, component, type) => {
    it('stores the namespace chosen after switching the selector mode', async () => {
      const stage = await renderStage(component, type);

      await setupUser().click(screen.getByRole('radio', { name: 'Choose a target dynamically' }));
      await chooseNamespace('team-b');

      await waitFor(() => expect(stage).toEqual(expect.objectContaining({ location: 'team-b', mode: 'dynamic' })));
    });

    it('stores the namespace chosen in the default selector mode', async () => {
      const stage = await renderStage(component, type);

      await chooseNamespace('team-a');

      await waitFor(() => expect(stage).toEqual(expect.objectContaining({ location: 'team-a', mode: 'static' })));
    });
  });

  it('stores Scale (Manifest) replicas after the manifest selector changes the stage', async () => {
    const stage = await renderStage(ScaleManifestStageConfig, 'scaleManifest');

    await setupUser().click(screen.getByRole('radio', { name: 'Choose a target dynamically' }));
    await chooseNamespace('team-b');
    fireEvent.change(within(getFormGroupByLabel('Replicas')).getByRole('spinbutton'), { target: { value: '3' } });

    await waitFor(() => expect(stage).toEqual(expect.objectContaining({ location: 'team-b', replicas: 3 })));
    expect(within(getFormGroupByLabel('Replicas')).getByRole('spinbutton')).toHaveValue(3);
  });
});
