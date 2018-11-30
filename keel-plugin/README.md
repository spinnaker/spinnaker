# keel-plugin

Core interfaces and functionality for asset and veto plugins. 
Specifically:

- Mapping from Kubernetes custom resource controller to Keel asset plugin.
- Asset monitoring to ensure out-of-band changes get spotted and stomped where appropriate.
- Mechanism to allow asset plugins to register their CRDs with Kubernetes.
