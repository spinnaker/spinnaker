export enum ManifestRenderers {
  HELM2 = 'HELM2',
  HELM3 = 'HELM3',
  KUSTOMIZE = 'KUSTOMIZE',
  KUSTOMIZE4 = 'KUSTOMIZE4',
}

export const HELM_RENDERERS: Readonly<ManifestRenderers[]> = [ManifestRenderers.HELM2, ManifestRenderers.HELM3];
export const KUSTOMIZE_RENDERERS: Readonly<ManifestRenderers[]> = [
  ManifestRenderers.KUSTOMIZE,
  ManifestRenderers.KUSTOMIZE4,
];
