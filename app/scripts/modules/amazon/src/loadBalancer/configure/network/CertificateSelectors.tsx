import React from 'react';

import { AmazonCertificateSelectField, IALBListenerCertificate } from '../../../index';
import { INLBCertificateSelectorProps } from '../network/NLBListeners';

export function CertificateSelector({
  availableCertificates,
  certificates,
  formik,
  app,
  certificateTypes,
}: INLBCertificateSelectorProps) {
  function certificateTypeChanged(certificate: IALBListenerCertificate, newType: string): void {
    certificate.type = newType;
    updateListeners();
  }

  function updateListeners(): void {
    formik.setFieldValue('listeners', formik.values.listeners);
  }

  function handleCertificateChanged(certificate: IALBListenerCertificate, newCertificateName: string): void {
    certificate.name = newCertificateName;
    updateListeners();
  }

  function showCertificateSelect(certificate: IALBListenerCertificate): boolean {
    return certificate.type === 'iam' && certificates && Object.keys(certificates).length > 0;
  }

  const { values } = formik;
  return (
    <div className="wizard-pod-row">
      <div className="wizard-pod-row-title">Certificate</div>
      <div className="wizard-pod-row-contents">
        {availableCertificates.map((certificate, cIndex) => (
          <div key={cIndex} style={{ width: '100%', display: 'flex', flexDirection: 'row' }}>
            <select
              className="form-control input-sm inline-number"
              style={{ width: '45px' }}
              value={certificate.type}
              onChange={(event) => certificateTypeChanged(certificate, event.target.value)}
            >
              {certificateTypes.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
            {showCertificateSelect(certificate) && (
              <AmazonCertificateSelectField
                certificates={certificates}
                accountName={values.credentials}
                currentValue={certificate.name}
                app={app}
                onCertificateSelect={(value) => handleCertificateChanged(certificate, value)}
              />
            )}
            {!showCertificateSelect(certificate) && (
              <input
                className="form-control input-sm no-spel"
                style={{ display: 'inline-block' }}
                type="text"
                value={certificate.name}
                onChange={(event) => handleCertificateChanged(certificate, event.target.value)}
                required={true}
              />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
