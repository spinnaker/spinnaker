import { REST } from '@spinnaker/core';

export interface IProxmoxTemplateImage {
  imageName: string;
  imageId: string;
  vmid: string;
  region: string;
  account: string;
  vmType: string;
}

/** Lists cached Proxmox templates via gate's /images/find route. */
export function listProxmoxTemplates(account?: string): PromiseLike<IProxmoxTemplateImage[]> {
  const query: Record<string, string> = { provider: 'proxmox', q: '*' };
  if (account) {
    query.account = account;
  }
  return REST('/images/find').query(query).get();
}
