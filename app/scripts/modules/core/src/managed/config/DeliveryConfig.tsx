import React from 'react';
import AceEditor from 'react-ace';

import { useApplicationContextSafe, useData } from 'core/presentation';

import { ManagedReader } from '..';
import { useLogEvent } from '../utils/logging';

export const DeliveryConfig = () => {
  const app = useApplicationContextSafe();
  const { result, error, status } = useData(() => ManagedReader.getDeliveryConfig(app.name), undefined, [app]);
  const logError = useLogEvent('DeliveryConfig');
  React.useEffect(() => {
    if (error) {
      logError({ action: 'LoadingFailed', data: { error } });
    }
  }, [error, logError]);

  return (
    <div className="DeliveryConfig sp-margin-xl-top">
      {status === 'REJECTED' && <div className="error-message">Failed to load delivery config</div>}
      {status === 'RESOLVED' && result && (
        <>
          <div>
            <h4>Delivery Config</h4>
          </div>
          <small>
            Note: The information below is derived from the delivery config that was sent to Spinnaker and is not
            necessarily identical to it.
          </small>
          <AceEditor
            mode="yaml"
            theme="textmate"
            readOnly
            fontSize={12}
            cursorStart={0}
            showPrintMargin={false}
            highlightActiveLine={true}
            maxLines={Infinity}
            value={result}
            setOptions={{
              firstLineNumber: 1,
              tabSize: 2,
              showLineNumbers: true,
              showFoldWidgets: true,
            }}
            style={{ width: 'auto' }}
            className="ace-editor sp-margin-s-top"
          />
        </>
      )}
    </div>
  );
};
