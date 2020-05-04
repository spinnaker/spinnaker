export interface IEbsBlockDevice {
  deleteOnTermination?: boolean;
  encrypted?: boolean;
  iops?: number;
  kmsKeyId?: string;
  snapshotId?: string;
  volumeSize?: number;
  volumeType?: string;
}

export interface IBlockDeviceMapping {
  deviceName?: string;
  ebs?: IEbsBlockDevice;
  noDevice?: string;
  virtualName?: string;
}
