import * as React from 'react';
import { SpelService } from './SpelService';

import {
  ITextInputProps,
  TextAreaInput,
  asyncMessage,
  messageMessage,
  useDebouncedValue,
  useInternalValidator,
  useIsMountedRef,
  useData,
  warningMessage,
} from 'core/presentation';

import './SpelInput.less';

interface ISpelInputProps extends ITextInputProps {
  previewExecutionId: string;
  previewStageId: string;
}

/** An Input that previews Spel expressions */
export function SpelInput(props: ISpelInputProps) {
  const { previewExecutionId, previewStageId, ...rest } = props;
  const [expression, isDebouncing] = useDebouncedValue(props.value, 300);
  const isInitialRender = !useIsMountedRef().current;
  const hasNewlines = !!/\n/.exec(rest.value);

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
      return messageMessage('Preview\n\n\n```\n' + JSON.stringify(result, null, 2) + '\n```');
    } else if (status === 'REJECTED') {
      return warningMessage(error);
    }
    return null;
  }

  useInternalValidator(props.validation, previewExpressionValidator);

  const fetchSpelPreview = useData(
    () => SpelService.evaluateExpression(expression, previewExecutionId, previewStageId),
    null,
    [expression, previewExecutionId, previewStageId],
  );

  // re-validate whenever an async event occurs
  React.useEffect(() => {
    !isInitialRender && props.validation && props.validation.revalidate && props.validation.revalidate();
  }, [fetchSpelPreview.status, isDebouncing]);

  return <TextAreaInput inputClassName="SpelInput" rows={hasNewlines ? 3 : 1} {...rest} />;
}
