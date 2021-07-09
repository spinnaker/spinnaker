import React from 'react';
import Select, { Option } from 'react-select';

import { Application, Overridable, relativeTime, timestamp } from '@spinnaker/core';
import { IAmazonCertificate } from '../../../domain';

export interface IAmazonCertificateSelectFieldProps {
  certificates: { [accountName: string]: IAmazonCertificate[] };
  accountName: string;
  currentValue: string;
  onCertificateSelect: (certificateName: string) => void;
  app: Application;
}

@Overridable('amazon.certificateSelectField')
export class AmazonCertificateSelectField extends React.Component<IAmazonCertificateSelectFieldProps> {
  public render() {
    const { certificates, accountName, onCertificateSelect, currentValue } = this.props;
    const certificatesForAccount = certificates[accountName] || [];
    const certificateOptions = certificatesForAccount.map((cert) => {
      return { label: cert.serverCertificateName, value: cert.serverCertificateName };
    });
    const currentCert = certificatesForAccount.find((c) => c.serverCertificateName === currentValue);
    return (
      <div style={{ width: '100%' }}>
        <Select
          className="input-sm"
          wrapperStyle={{ width: '100%' }}
          clearable={true}
          required={true}
          options={certificateOptions}
          onChange={(value: Option<string>) => onCertificateSelect(value.value)}
          value={currentValue}
        />
        {currentCert && (
          <div className="small sp-margin-xs-top sp-margin-m-bottom sp-margin-m-left">
            <div>
              Uploaded {relativeTime(currentCert.uploadDate)} ({timestamp(currentCert.uploadDate)})
            </div>
            <b>Expires {relativeTime(currentCert.expiration)}</b> ({timestamp(currentCert.expiration)})
          </div>
        )}
      </div>
    );
  }
}
