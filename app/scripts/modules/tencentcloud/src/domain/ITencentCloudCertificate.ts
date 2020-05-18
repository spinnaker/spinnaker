import { ICertificate } from '@spinnaker/core';

export interface ITencentCloudCertificate extends ICertificate {
  arn: string;
  uploadDate: number;
}
