import * as React from 'react';
import Select, { Option } from 'react-select';
import { Application, Overridable } from '@spinnaker/core';

import { IAmazonCertificate } from 'amazon/domain';

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
    const certificateOptions = certificatesForAccount.map(cert => {
      return { label: cert.serverCertificateName, value: cert.serverCertificateName };
    });
    return (
      <Select
        className="input-sm"
        wrapperStyle={{ width: '100%' }}
        clearable={true}
        required={true}
        options={certificateOptions}
        onChange={(value: Option<string>) => onCertificateSelect(value.value)}
        value={currentValue}
      />
    );
  }
}
