import React from 'react';

import { CheckboxInput } from 'core/presentation';
import { ITriggerTemplateComponentProps } from '../../../manualExecution/TriggerTemplate';
import { IArtifact, IExpectedArtifact } from 'core/domain';
import { HelmMatch } from '../../triggers/artifacts/helm/HelmArtifactEditor';
import { BAKE_MANIFEST_STAGE_KEY } from './bakeManifestStage';

const HelmEditor = HelmMatch.editCmp;

export function ManualExecutionBakeManifest(props: ITriggerTemplateComponentProps) {
  const [overrideArtifact, setOverrideArtifact] = React.useState(false);

  const updateHelmArtifact = (artifact: IArtifact) => {
    const updatedArtifacts = (props.command.extraFields.artifacts || []).filter(
      (a: IArtifact) => a.type !== HelmMatch.type,
    );
    updatedArtifacts.push(artifact);
    props.updateCommand('extraFields.artifacts', updatedArtifacts);
  };

  const removeHelmArtifact = () => {
    const updatedArtifacts = (props.command.extraFields.artifacts || []).filter(
      (a: IArtifact) => a.type !== HelmMatch.type,
    );
    props.updateCommand('extraFields.artifacts', updatedArtifacts);
  };

  /*
  Only allow manual override of a helm chart artifact when there is exactly one Helm
  Bake (Manifest) stage and exactly one artifact of type `helm/chart`.
   */
  const bakeManifestStages = props.command.pipeline.stages.filter(stage => stage.type === BAKE_MANIFEST_STAGE_KEY);
  if (bakeManifestStages.length !== 1 || bakeManifestStages[0].templateRenderer !== 'HELM2') {
    return null;
  }
  const expectedArtifacts = props.command.pipeline.expectedArtifacts || [];
  const expectedHelmArtifacts = expectedArtifacts.filter(
    (artifact: IExpectedArtifact) => artifact.matchArtifact.type === HelmMatch.type,
  );
  if (expectedHelmArtifacts.length !== 1) {
    return null;
  }

  const helmArtifact = (props.command.extraFields.artifacts || []).find((a: IArtifact) => a.type === HelmMatch.type);
  const defaultArtifact: IArtifact = {
    ...expectedHelmArtifacts[0].matchArtifact,
    version: null,
  };

  React.useEffect(() => {
    if (overrideArtifact === false) {
      removeHelmArtifact();
    } else {
      updateHelmArtifact(defaultArtifact);
    }
  }, [overrideArtifact]);

  return (
    <>
      <div className="form-group">
        <div className="sm-label-right col-md-4">Helm override</div>
        <div className="col-md-8">
          <CheckboxInput
            checked={overrideArtifact}
            onChange={(e: any) => setOverrideArtifact(e.target.checked)}
            text="Override Helm chart artifact"
          />
        </div>
      </div>
      {overrideArtifact && (
        <div className="form-group">
          <div className="col-md-2" />
          <div className="col-md-10">
            <HelmEditor
              account={{ name: expectedHelmArtifacts[0].matchArtifact.artifactAccount, types: [HelmMatch.type] }}
              artifact={helmArtifact || defaultArtifact}
              pipeline={props.command.pipeline}
              onChange={updateHelmArtifact}
            />
          </div>
        </div>
      )}
    </>
  );
}
