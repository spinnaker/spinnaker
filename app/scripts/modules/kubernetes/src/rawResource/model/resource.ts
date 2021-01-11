interface IResource {
  name: String;
  labels: {
    [key: string]: String;
  };
}

interface IApiKubernetesResource {
  account: string;
  apiVersion: string;
  displayName: string;
  kind: string;
  labels: Record<string, string>;
  moniker: Record<string, string>;
  name: string;
  namespace: string;
  region: string;
}
