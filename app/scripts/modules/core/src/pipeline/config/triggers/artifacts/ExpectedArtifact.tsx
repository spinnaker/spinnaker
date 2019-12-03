import React from 'react';
import { clone, set } from 'lodash';

import { IExpectedArtifact, IPipeline } from 'core/domain';
import { HelpField } from 'core/help';
import { CheckboxInput, FormField, TextInput, Tooltip, useLatestPromise } from 'core/presentation';
import { AccountService } from 'core/account';
import { ArtifactEditor } from 'core/artifact/react/ArtifactEditor';

export interface IExpectedArtifactProps {
  expectedArtifact: IExpectedArtifact;
  removeExpectedArtifact: (conext: any, expectedArtifact: IExpectedArtifact) => void;
  updateExpectedArtifact: (expectedArtifact: IExpectedArtifact) => void;
  usePriorExecution?: boolean;
  pipeline: IPipeline;
}

export function ExpectedArtifact(props: IExpectedArtifactProps) {
  const { expectedArtifact, removeExpectedArtifact, updateExpectedArtifact, pipeline, usePriorExecution } = props;
  const [artifactAccounts, setArtifactAccounts] = React.useState([]);

  function updateUpdatedArtifact(path: string, value: any): void {
    const newExpectedArtifact = clone(expectedArtifact);
    set(newExpectedArtifact, path, value);
    updateExpectedArtifact(newExpectedArtifact);
  }

  const fetchArtifactAccounts = useLatestPromise(() => {
    return AccountService.getArtifactAccounts();
  }, []);

  React.useEffect(() => {
    if (fetchArtifactAccounts.result) {
      setArtifactAccounts(fetchArtifactAccounts.result);
    }
  }, [fetchArtifactAccounts.result]);

  return (
    <div>
      <div className="form-horizontal panel-pipeline-phase">
        <div className="form-horizontal panel-pipeline-phase">
          <div className="form-group row">
            <div className="col-md-3">
              Match against
              <HelpField id="pipeline.config.expectedArtifact.matchArtifact" />
            </div>
            <div className="col-md-2 col-md-offset-7">
              <Tooltip value="Remove expected artifact">
                <button
                  className="btn btn-sm btn-default"
                  onClick={() => removeExpectedArtifact(pipeline, expectedArtifact)}
                >
                  <span className="glyphicon glyphicon-trash" />
                  <span>Remove artifact</span>
                </button>
              </Tooltip>
            </div>
          </div>
          <ArtifactEditor
            pipeline={pipeline}
            artifact={expectedArtifact.matchArtifact}
            artifactAccounts={artifactAccounts}
            onArtifactEdit={artifact => updateUpdatedArtifact('matchArtifact', artifact)}
            isDefault={false}
          />
          <hr />
          <FormField
            name="displayName"
            label="Display name"
            value={expectedArtifact.displayName}
            input={inputProps => <TextInput {...inputProps} />}
            onChange={event => updateUpdatedArtifact('displayName', event.target.value)}
          />
          <hr />
          <div className="form-group row">
            <div className="col-md-3">
              If missing <HelpField id="pipeline.config.expectedArtifact.ifMissing" />
            </div>
          </div>
          {!!usePriorExecution && (
            <FormField
              name="usePriorArtifact"
              label="Use Prior Execution"
              value={expectedArtifact.usePriorArtifact}
              input={inputProps => <CheckboxInput {...inputProps} />}
              onChange={() => updateUpdatedArtifact('usePriorArtifact', !expectedArtifact.usePriorArtifact)}
            />
          )}
          <FormField
            name="useDefaultArtifact"
            label="Use Default Artifact"
            value={expectedArtifact.useDefaultArtifact}
            input={inputProps => <CheckboxInput {...inputProps} />}
            onChange={() => updateUpdatedArtifact('useDefaultArtifact', !expectedArtifact.useDefaultArtifact)}
          />
          {expectedArtifact.useDefaultArtifact && (
            <>
              <div className="form-group row" style={{ height: '30px' }}>
                <div className="col-md-3">
                  Default artifact
                  <HelpField id="pipeline.config.expectedArtifact.defaultArtifact" />
                </div>
              </div>
              <ArtifactEditor
                pipeline={pipeline}
                artifact={expectedArtifact.defaultArtifact}
                artifactAccounts={artifactAccounts}
                onArtifactEdit={artifact => updateUpdatedArtifact('defaultArtifact', artifact)}
                isDefault={true}
              />
            </>
          )}
        </div>
      </div>
    </div>
  );
}
