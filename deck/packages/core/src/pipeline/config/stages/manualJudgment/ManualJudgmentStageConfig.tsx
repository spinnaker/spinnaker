import React from 'react';

import type { IStageConfigProps } from '../common';
import { StageConfigField } from '../common';
import { SETTINGS } from '../../../../config/settings';
import type { INotification } from '../../../../domain';
import { NotificationsList } from '../../../../notification';

interface IJudgmentInput {
  value?: string;
}

export function ManualJudgmentStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;

  if (!stage.notifications) {
    stage.notifications = [];
  }
  if (!stage.judgmentInputs) {
    stage.judgmentInputs = [];
  }
  if (stage.failPipeline === undefined) {
    stage.failPipeline = true;
  }

  const judgmentInputs = stage.judgmentInputs as IJudgmentInput[];

  const updateJudgmentInputs = (inputs: IJudgmentInput[]) => updateStageField({ judgmentInputs: inputs });
  const updateNotifications = (notifications: INotification[]) => updateStageField({ notifications });

  const handleSendNotificationsChanged = (event: React.ChangeEvent<HTMLInputElement>) => {
    const sendNotifications = event.target.checked;
    updateStageField(sendNotifications ? { sendNotifications } : { sendNotifications: undefined, notifications: [] });
  };

  return (
    <div className="form-horizontal">
      <StageConfigField label="Instructions" helpKey="pipeline.config.manualJudgment.instructions">
        <textarea
          className="form-control input-sm"
          value={stage.instructions || ''}
          placeholder="Provide any instructional text that would assist making a manual judgment (can contain HTML)"
          onChange={(event) => updateStageField({ instructions: event.target.value })}
        />
      </StageConfigField>
      {SETTINGS.authEnabled && (
        <div>
          <StageConfigField
            label="Propagate Authentication"
            helpKey="pipeline.config.manualJudgment.propagateAuthentication"
          >
            <div className="checkbox">
              <label>
                <input
                  type="checkbox"
                  checked={!!stage.propagateAuthenticationContext}
                  onChange={(event) => updateStageField({ propagateAuthenticationContext: event.target.checked })}
                />
              </label>
            </div>
          </StageConfigField>
          <StageConfigField label="Prevent Self Approval" helpKey="pipeline.config.manualJudgment.preventSelfApproval">
            <div className="checkbox">
              <label>
                <input
                  type="checkbox"
                  checked={!!stage.preventSelfApproval}
                  onChange={(event) => updateStageField({ preventSelfApproval: event.target.checked })}
                />
              </label>
            </div>
          </StageConfigField>
        </div>
      )}
      <StageConfigField label="Judgment Inputs" helpKey="pipeline.config.manualJudgment.judgmentInputs">
        <table className="table table-condensed packed">
          <thead>
            <tr>
              <th style={{ width: '30%' }}>Option</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {judgmentInputs.map((judgmentInput, index) => (
              <tr key={index}>
                <td>
                  <input
                    type="text"
                    required={true}
                    value={judgmentInput.value || ''}
                    className="form-control input-sm"
                    onChange={(event) => {
                      const inputs = judgmentInputs.slice();
                      inputs[index] = { ...inputs[index], value: event.target.value };
                      updateJudgmentInputs(inputs);
                    }}
                  />
                </td>
                <td className="text-right">
                  <a
                    className="small"
                    href="#"
                    onClick={(event) => {
                      event.preventDefault();
                      updateJudgmentInputs(judgmentInputs.filter((_, inputIndex) => inputIndex !== index));
                    }}
                  >
                    Remove
                  </a>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={7}>
                <button
                  type="button"
                  className="btn btn-block btn-sm add-new"
                  onClick={() => updateJudgmentInputs(judgmentInputs.concat({}))}
                >
                  <span className="glyphicon glyphicon-plus-sign" /> Add judgment input
                </button>
              </td>
            </tr>
          </tfoot>
        </table>
      </StageConfigField>
      <StageConfigField label="Send Notifications">
        <div className="checkbox">
          <label>
            <input type="checkbox" checked={!!stage.sendNotifications} onChange={handleSendNotificationsChanged} />
          </label>
        </div>
      </StageConfigField>
      {stage.sendNotifications && (
        <div className="row">
          <StageConfigField label="Notifications">
            <NotificationsList
              level="stage"
              stageType="manualJudgment"
              notifications={stage.notifications}
              sendNotifications={stage.sendNotifications}
              updateNotifications={updateNotifications}
            />
          </StageConfigField>
        </div>
      )}
    </div>
  );
}
