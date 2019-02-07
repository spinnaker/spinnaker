# keel-plugin

Core interfaces and functionality for resource and veto plugins. 
Specifically:

- Mapping from Kubernetes custom resource controller to Keel resource plugin.
- Resource monitoring to ensure out-of-band changes get spotted and stomped where appropriate.
- Mechanism to allow resource plugins to register their CRDs with Kubernetes.
