export interface IGceDisk {
  boot: boolean;
  deviceName: string;
  index: number;
  initializeParams: {
    diskSizeGb: number;
    diskType: string;
    sourceImage: string;
  };
}
