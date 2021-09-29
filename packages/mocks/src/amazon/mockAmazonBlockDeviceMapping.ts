import type { IEbsBlockDevice } from '@spinnaker/amazon';
import { IBlockDeviceMapping } from '@spinnaker/amazon';

export const createMockEbsBlockDevice = (options?: { size?: number; type?: string }): IEbsBlockDevice => ({
  deleteOnTermination: false,
  encrypted: true,
  volumeSize: options?.size || 40,
  volumeType: options?.type || 'standard',
});

export const createMockBlockDeviceMapping = (customEbs?: IEbsBlockDevice) => ({
  deviceName: '/dev/sdb',
  ebs: customEbs || createMockEbsBlockDevice(),
});
