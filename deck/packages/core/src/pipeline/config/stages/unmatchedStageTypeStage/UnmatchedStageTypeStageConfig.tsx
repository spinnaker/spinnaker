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

function makeCleanStageCopy(stage: IStage): Record<string, unknown> {
  const stageCopy = cloneDeep(stage || {}) as Record<string, unknown>;
  keysToHide.forEach((key) => {
    if (stageCopy[key] !== undefined) {
      delete stageCopy[key];
    }
  });
  return stageCopy;
}

function getUnmatchedStageJson(stage: IStage): string {
  return JsonUtils.makeSortedStringFromObject(makeCleanStageCopy(stage));
}

interface IUnmatchedStageJsonResult {
  errorMessage: string;
  stage?: IStage;
  stageJson: string;
}

function parseUnmatchedStageJson(stage: IStage, stageJson: string): IUnmatchedStageJsonResult {
  let parsedStage: IStage;
  try {
    parsedStage = JSON.parse(stageJson);
  } catch (error) {
    return { errorMessage: error.message, stageJson };
  }

  if (!parsedStage.type) {
    return { errorMessage: 'Cannot delete property type.', stageJson };
  }

  const nextStage = cloneDeep(stage);
  Object.keys(nextStage).forEach((key) => {
    if (!keysToHide.has(key)) {
      delete nextStage[key];
    }
  });
  Object.assign(nextStage, parsedStage);

  const cleanStageCopy = makeCleanStageCopy(nextStage);
  return {
    errorMessage: null,
    stage: nextStage,
    stageJson: isEqual(cleanStageCopy, parsedStage) ? stageJson : JsonUtils.makeStringFromObject(cleanStageCopy),
  };
}

export function UnmatchedStageTypeStageConfig({ stage, stageFieldUpdated }: IStageConfigProps) {
  const [stageJson, setStageJson] = React.useState(() => getUnmatchedStageJson(stage));
  const [errorMessage, setErrorMessage] = React.useState<string>(null);

  const updateStage = (nextStageJson: string) => {
    setStageJson(nextStageJson);
    const result = parseUnmatchedStageJson(stage, nextStageJson);
    setErrorMessage(result.errorMessage);
    if (!result.stage) {
      return;
    }

    Object.keys(stage).forEach((key) => {
      delete stage[key];
    });
    Object.assign(stage, result.stage);
    stageFieldUpdated();

    if (result.stageJson !== nextStageJson) {
      setStageJson(result.stageJson);
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
