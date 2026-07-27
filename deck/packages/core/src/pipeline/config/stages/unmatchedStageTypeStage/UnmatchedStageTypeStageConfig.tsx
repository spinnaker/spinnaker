import { cloneDeep, isEqual } from 'lodash';
import React from 'react';

import type { IStageConfigProps } from '../common';
import type { IStage } from '../../../../domain';
import { JsonEditor } from '../../../../presentation';
import { JsonUtils } from '../../../../utils';

const keysToHide = new Set<string>([
  'refId',
  'requisiteStageRefIds',
  'failPipeline',
  'continuePipeline',
  'completeOtherBranchesThenFail',
  'restrictExecutionDuringTimeWindow',
  'restrictedExecutionWindow',
  'stageEnabled',
  'sendNotifications',
  'notifications',
  'comments',
  'name',
]);

function makeCleanStageCopy(stage: IStage): Record<string, any> {
  const stageCopy = cloneDeep(stage || {}) as Record<string, any>;
  keysToHide.forEach((key) => {
    if (stageCopy[key] !== undefined) {
      delete stageCopy[key];
    }
  });
  return stageCopy;
}

export function UnmatchedStageTypeStageConfig({ stage, stageFieldUpdated }: IStageConfigProps) {
  const [stageJson, setStageJson] = React.useState(() =>
    JsonUtils.makeSortedStringFromObject(makeCleanStageCopy(stage)),
  );
  const [errorMessage, setErrorMessage] = React.useState<string>(null);

  const updateStage = (nextStageJson: string) => {
    setStageJson(nextStageJson);
    setErrorMessage(null);

    let parsedStage: IStage;
    try {
      parsedStage = JSON.parse(nextStageJson);
    } catch (error) {
      setErrorMessage(error.message);
      return;
    }

    if (!parsedStage.type) {
      setErrorMessage('Cannot delete property type.');
      return;
    }

    Object.keys(stage).forEach((key) => {
      if (!keysToHide.has(key)) {
        delete stage[key];
      }
    });
    Object.assign(stage, parsedStage);
    stageFieldUpdated();

    const cleanStageCopy = makeCleanStageCopy(stage);
    if (!isEqual(cleanStageCopy, parsedStage)) {
      setStageJson(JsonUtils.makeStringFromObject(cleanStageCopy));
    }
  };

  return (
    <div>
      <form name="form" className="form-horizontal flex-fill">
        <div className="flex-fill">
          <JsonEditor value={stageJson} onChange={updateStage} minLines={Math.max(stageJson.split('\n').length, 5)} />
        </div>
      </form>
      {errorMessage && (
        <div className="form-group row" style={{ marginTop: 10 }}>
          <div className="col-md-9 col-md-offset-3 error-message slide-in">Error: {errorMessage}</div>
        </div>
      )}
    </div>
  );
}
