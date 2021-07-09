import { ICertificate } from '@spinnaker/core';

export interface ITencentcloudCertificate extends ICertificate {
  arn: string;
  uploadDate: number;
}
