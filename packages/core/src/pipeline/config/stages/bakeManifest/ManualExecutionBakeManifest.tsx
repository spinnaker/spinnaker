import React from 'react';

import { IArtifact, IExpectedArtifact, ITrigger } from '../../../../domain';
import { ITriggerTemplateComponentProps } from '../../../manualExecution/TriggerTemplate';
import { CheckboxInput } from '../../../../presentation';
import { HelmMatch } from '../../triggers/artifacts/helm/HelmArtifactEditor';
import { HELM_TRIGGER_TYPE } from '../../triggers/helm/helm.trigger';

const HelmEditor = HelmMatch.editCmp;

export function ManualExecutionBakeManifest(props: ITriggerTemplateComponentProps) {
  const [overrideArtifact, setOverrideArtifact] = React.useState(true);

  const defaultArtifact: IArtifact = React.useMemo(() => {
    const expectedHelmArtifacts = (props.command.pipeline.expectedArtifacts || []).filter(
      (artifact: IExpectedArtifact) => artifact.matchArtifact.type === HelmMatch.type,
    );
    if (expectedHelmArtifacts.length === 0) {
      return null;
    }
    // If we have a Helm trigger configured, use the trigger's manual execution component instead
    const helmTriggersConfigured = (props.command.pipeline.triggers || []).filter(
      (trigger: ITrigger) => trigger.type === HELM_TRIGGER_TYPE,
    );
    if (helmTriggersConfigured.length !== 0) {
      return null;
    }
    return {
      ...expectedHelmArtifacts[0].matchArtifact,
      version: null,
    };
  }, []);

  React.useEffect(() => {
    if (overrideArtifact === false) {
      removeHelmArtifact();
    } else if (defaultArtifact) {
      updateHelmArtifact(defaultArtifact);
    }
  }, [defaultArtifact, overrideArtifact]);

  const helmArtifact = (props.command.extraFields.artifacts || []).find((a: IArtifact) => a.type === HelmMatch.type);

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

  if (!defaultArtifact) {
    return null;
  }

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
              account={{ name: defaultArtifact.artifactAccount, types: [HelmMatch.type] }}
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
