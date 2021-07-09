import React from 'react';

import { SpelService } from './SpelService';
import { ITextInputProps, TextAreaInput } from '../forms/inputs';
import { useInternalValidator } from '../forms/inputs/hooks';
import { asyncMessage, messageMessage, warningMessage } from '../forms/validation';
import { useData, useDebouncedValue, useIsMountedRef } from '../hooks';

import './SpelInput.less';

export interface IStageForSpelPreview {
  executionLabel: string;
  executionId: string;
  stageId: string;
}

interface ISpelInputProps extends ITextInputProps {
  previewStage: IStageForSpelPreview;
}

/** An Input that previews Spel expressions */
export function SpelInput(props: ISpelInputProps) {
  const { previewStage, ...rest } = props;
  const [expression, isDebouncing] = useDebouncedValue(props.value, 300);
  const isInitialRender = !useIsMountedRef().current;

  function previewExpressionValidator() {
    const { status, error, result } = fetchSpelPreview;
    if (status === 'NONE') {
      return null;
    } else if (status === 'PENDING' || isDebouncing) {
      if (result) {
        return asyncMessage('Updating preview...\n\n\n```\n' + JSON.stringify(result, null, 2) + '\n```');
      }
      return asyncMessage('Updating preview...');
    } else if (status === 'RESOLVED') {
      // Render as a code block
      const previewMessage =
        `Preview - based on previous execution: ${previewStage.executionLabel}\n\n\n` +
        '```\n' +
        JSON.stringify(result, null, 2) +
        '\n```';
      return messageMessage(previewMessage);
    } else if (status === 'REJECTED') {
      return warningMessage(error);
    }
    return null;
  }

  useInternalValidator(props.validation, previewExpressionValidator);

  const fetchSpelPreview = useData(
    () => SpelService.evaluateExpression(expression, previewStage.executionId, previewStage.stageId),
    null,
    [expression, previewStage.executionId, previewStage.stageId],
  );

  // re-validate whenever an async event occurs
  React.useEffect(() => {
    !isInitialRender && props.validation && props.validation.revalidate && props.validation.revalidate();
  }, [fetchSpelPreview.status, isDebouncing]);

  return <SimpleSpelInput {...rest} />;
}

/** An Input for entering SpEL without preview functionality */
export function SimpleSpelInput(props: ITextInputProps) {
  const hasNewlines = !!/\n/.exec(props.value);
  return (
    <TextAreaInput
      inputClassName="SpelInput"
      rows={hasNewlines ? 3 : 1}
      placeholder={props.placeholder || 'Variable value, e.g. ${trigger.buildInfo.number}'}
      {...props}
    />
  );
}
