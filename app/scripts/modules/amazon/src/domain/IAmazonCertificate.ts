import { ICertificate } from '@spinnaker/core';

export interface IAmazonCertificate extends ICertificate {
  arn: string;
  uploadDate: number;
}
