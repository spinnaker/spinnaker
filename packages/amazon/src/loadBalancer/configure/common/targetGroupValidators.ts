import { get, isNumber } from 'lodash';

import { IValidator } from '@spinnaker/core';
import { ITargetGroup } from '../../../domain';

export const isNameInUse = (
  existingNames: { [account: string]: { [region: string]: string[] } },
  credentials: string,
  region: string,
): IValidator => (name: string) =>
  get(existingNames, [credentials, region], []).includes(name.toLowerCase())
    ? `There is already a target group in ${credentials}:${region} with that name.`
    : null;

export const isNameLong = (appNameLength: number): IValidator => (name: string) =>
  name.length < 32 - appNameLength
    ? null
    : 'Target group names are automatically prefixed with their application name and cannot exceed 32 characters in length.';

export const isValidTimeout = (targetGroup: ITargetGroup): IValidator => (value: number | string) => {
  const num = isNumber(value) ? value : Number.parseInt(value, 10);
  const { protocol, healthCheckProtocol } = targetGroup;

  if (protocol === 'TCP' || protocol === 'TLS') {
    if (healthCheckProtocol === 'HTTP' && num !== 6) {
      return 'HTTP health check timeouts for TCP/TLS target groups must be 6s';
    }

    if ((healthCheckProtocol === 'HTTPS' || healthCheckProtocol === 'TLS') && num !== 10) {
      return 'HTTPS/TLS health check timeouts for TCP/TLS target groups must be 10s';
    }
  }
  return null;
};

export const isValidHealthCheckInterval = (targetGroup: ITargetGroup): IValidator => (value: string) =>
  targetGroup.healthCheckProtocol !== 'TCP' ||
  (targetGroup.healthCheckProtocol === 'TCP' &&
    (Number.parseInt(value, 10) === 10 || Number.parseInt(value, 10) === 30))
    ? null
    : 'TCP health checks only support 10s and 30s intervals';
