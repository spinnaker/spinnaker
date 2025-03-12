import React from 'react';

import { RelativeTimestamp } from '../RelativeTimestamp';
import type { FetchApplicationManagementDataQueryVariables } from '../graphql/graphql-sdk';
import { FetchApplicationManagementDataDocument, useImportDeliveryConfigMutation } from '../graphql/graphql-sdk';
import { useApplicationContextSafe } from '../../presentation/hooks/useApplicationContext.hook';
import { YamlViewer } from '../utils/YamlViewer';
import { useLogEvent } from '../utils/logging';
import { useNotifyOnError } from '../utils/useNotifyOnError.hook';
import { Spinner } from '../../widgets';

interface IDeliveryConfigProps {
  config?: string;
  updatedAt?: string;
  isProcessed?: boolean;
}

const ReImportConfig = () => {
  const appName = useApplicationContextSafe().name;
  const refetchVariables: FetchApplicationManagementDataQueryVariables = { appName };
  const [importDeliveryConfig, { error, loading }] = useImportDeliveryConfigMutation({
    variables: { application: appName },
    refetchQueries: [{ query: FetchApplicationManagementDataDocument, variables: refetchVariables }],
  });
  const logEvent = useLogEvent('GitIntegration', 'ImportNow');

  useNotifyOnError({ key: 'import-error', content: `Failed to import delivery config`, error });

  return (
    <>
      <small>
        (
        <button
          className="btn-link no-padding no-margin no-border"
          onClick={() => {
            importDeliveryConfig();
            logEvent();
          }}
          disabled={loading}
        >
          Import now
        </button>
        )
      </small>
      {loading && <Spinner mode="circular" size="nano" color="var(--color-accent)" className="sp-margin-xs-left" />}
    </>
  );
};

export const DeliveryConfig: React.FC<IDeliveryConfigProps> = ({ config, updatedAt, isProcessed, children }) => {
  return (
    <div className="sp-margin-xl-top">
      <div className="sp-margin-m-bottom">
        <h4 className="sp-margin-3xs-bottom">{isProcessed ? 'Processed Delivery Config' : 'Delivery config'}</h4>
        <div className="horizontal middle sp-margin-xs-top">
          {updatedAt && (
            <small className="sp-margin-xs-right">
              Last update: <RelativeTimestamp timestamp={updatedAt} withSuffix removeStyles />
            </small>
          )}
          {!isProcessed && <ReImportConfig />}
        </div>
      </div>
      {children}
      {config && <YamlViewer content={config} />}
    </div>
  );
};
