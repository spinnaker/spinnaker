import { isEmpty } from 'lodash';

export const iamRoleValidator = (value: string, label: string) => {
  const isIAMRole = value.match(/^arn:aws:iam::\d{12}:role\/?\/[a-zA-Z_0-9+=,.@\-_/]+/);
  return isIAMRole
    ? undefined
    : `Invalid role.  ${label} must match regular expression: arn:aws:iam::d{12}:role/?[a-zA-Z_0-9+=,.@-_/]+`;
};

export const s3BucketNameValidator = (value: string, label: string) => {
  const s3BucketName = value.match(/^[0-9A-Za-z.-]*[^.]$/);
  const err = s3BucketName
    ? undefined
    : `Invalid S3 Bucket name.  ${label} must match regular expression: [0-9A-Za-z.-]*[^.]$`;
  return err;
};

export const awsArnValidator = (value: string, label: string) => {
  const arn = value.match(/arn:aws[a-zA-Z-]?:[a-zA-Z_0-9.-]+:./);
  return arn
    ? undefined
    : `Invalid ARN.  ${label} must match regular expression: /arn:aws[a-zA-Z-]?:[a-zA-Z_0-9.-]+:./`;
};

export const awsTagsValidator = (value: string | { [key: string]: string }, label: string) => {
  return isEmpty(value) ? `At least one ${label} is required` : undefined;
};
