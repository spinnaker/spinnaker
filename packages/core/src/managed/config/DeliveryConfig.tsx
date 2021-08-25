import React from 'react';
import { RelativeTimestamp } from '../RelativeTimestamp';
import { YamlViewer } from '../utils/YamlViewer';

interface IDeliveryConfigProps {
  config?: string;
  updatedAt?: string;
  isProcessed?: boolean;
}

export const DeliveryConfig: React.FC<IDeliveryConfigProps> = ({ config, updatedAt, isProcessed, children }) => {
  return (
    <div className="sp-margin-xl-top">
      <div className="sp-margin-m-bottom">
        <h4 className="sp-margin-3xs-bottom">{isProcessed ? 'Processed Delivery Config' : 'Delivery config'}</h4>

        {updatedAt && (
          <small>
            Last update: <RelativeTimestamp timestamp={updatedAt} withSuffix removeStyles />
          </small>
        )}
      </div>
      {children}
      {config && <YamlViewer content={config} />}
    </div>
  );
};
