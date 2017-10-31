# Running the provider...

For any account, add `providerVersion: v2` as a sibling to `name` and other account-level fields. e.g.

```yaml
kubernetes:
  enabled: true
  accounts:
  - name: k8s-v2
    context: my_context_in_the_kubeconfig
    providerVersion: v2
```
