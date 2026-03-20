# Entity Storage

## Overview

Before diving into the specifics, we need to define what an entity is. An entity is just any data. This could be a map in a context, a whole class stored in SQL, anything.
With the introduction of the entity storage, we are no longer limited to just storing Spinnaker Artifact classes, but can store any entity by registering the hook registries.

Entity storage is quite flexible in that Spinnaker developers may opt to choose which entities they want to store. This can be done by
modifying the default beans in the `EntityStoreConfiguration` to inject specific handlers into, both, the `SerializerHookRegistry` and `DeserializerHookRegistry`.

## Why

Manifests can be quite large and are duplicated many times within the JSON execution, especially if referenced by other stages.
For a simple nginx Kubernetes manifest, we saw a ~40% reduction in that stage size.

For larger and more complex pipelines that represent the real world, we saw upwards of 85% reduction in execution size.

Further, we have seen several features go out to compress execution context sizes. Rather than relying on various different implementation,
this should enable any Spinnaker developer to reduce the execution context with some minor configuration.

## Design

This uses the modifier concept in Jackson. We inject custom modifiers for both the serializers and deserializers. This allows us to retain prior custom (de)serializers and handle jackson/object mapper annotations without having to re-implement them ourselves.
The entities' package have two main modifiers, the SerializerHookRegistry and the DeserializerHookRegistry. When adding any custom entity related storage, the hooks need to be added here.
Further, it is VERY important that for serialization only the object being serialized is modified, and that the serializer that is passed in does the serialization.
The same is true for deserialization, except that we ONLY affect the AST and then rely on the provided deserializer to do the work.

### Handler Pattern

This utilizes the handler pattern which allows for any sort of handling of keys or fields, which readies the object for serialization or deserialization.

There are two different serialization handlers, `ArtifactStorageHandler` and `ArtifactStoragePropertyHandler`. The `ArtifactStorageHandler` handles data massaging regardless of the field. However, `ArtifactStoragePropertyHandler` allows specific handler for particular fields, which makes it much more specific than the `ArtifactStorageHandler`.

Below we will show a custom handler that removes the `secret` key in `Map`s.
```java
public class SecretKeyRemoverHandler implements ArtifactStorageHandler {
    @Override
    public boolean canHandle(Object v) {
        return v instanceof Map;
    }
    public <V> V handle(ArtifactStore store, V v, ObjectMapper objectMapper) {
        Map m = (Map) v;
        if (!m.containsKey("secret")) {
            return v;
        }
        m.remove("secret");
        return m;
    }
}
```

Note that this is not using the artifact store at all, but allows us to massage the data however needed.

Let's do the same concept, but instead of using the handler we defined above, we only want to do it for fields called `secrets`.

```java
public class SecretKeyRemoverHandler implements ArtifactStoragePropertyHandler {
    @Override
    boolean canHandleProperty(BeanProperty property, Object v) {
        return "secrets".equals(property.getName()) && v instanceof Map;
    }
    public <T> T handleProperty(ArtifactStore store, BeanProperty property, T v, ObjectMapper objectMapper) {
        Map m = (Map) v;
        if (!m.containsKey("secret")) {
            return v;
        }
        m.remove("secret");
        return m;
    }
}
```

While the handling methods look *very* similar, the key difference is the `ArtifactStorageHandler` does it for *any* map, while the `ArtifactStoragePropertyHandler` does it only for fields named `secrets`.

### Manifests

#### How

#### Artifact Store
Spinnaker already has support for Artifact storage. This package extends support for allowing manifests to be used as artifacts.

Generally, when an artifact is stored it is converted to a `remote/base64` artifact. This proposed two options: reuse the `remote/base64` or introduce a new type, `remote/map/base64`

While it may seem like a good idea to rely on the pre-existing `remote/base64` type this proposes major issues around expected artifacts. Expected artifacts and artifact resolution are a fairly complicated process in Spinnaker.
When artifact has mismatched types, e.g. `embedded/base64` vs `helm/chart`, they will not resolve against different types.
So why does `embedded/base64` work seamlessly with `remote/base64`?
When introducing the new `remote/base64` type we wanted to remain backwards compatible.
So we added logic to ensure that any comparison between the two would ensure they could be properly matched against.
However, if manifests re-used `remote/base64`, users may use SpEL and inject this manifest artifact resulting in odd behavior, because manifests are not Spinnaker artifacts.
Instead, introducing a new type `remote/map/base64` keeps the expected artifact to work appropriately, and also ensures serializer and deserializer handle expansion and storage appropriately based off type.

We rely on the same patterns as the artifact store where we will use serializers and deserializers to handle storage and expansions. The key difference with manifests is that we have to rely on map keys to dictate whether we should store the object.

### SpEL

The main goal for SpEL is to ensure SpEL expressions are as backwards compatible as possible.
We introduce a new map property accessor which will look to see if the artifact type is `remote/map/base64` and if it is, then expand it.

```
${ #stage('Deploy Manifest').context.manifests

# would evaluate to

[
  {
    "customKind": false,
    "reference": "ref://bjp/867867a8e7e9da56bc108b97c03bf3b532f86e9cfdf3196de277a2d77d188195",
    "metadata": {},
    "name": "stored-entity",
    "type": "remote/map/base64"
  },
  {
    "customKind": false,
    "reference": "ref://bjp/d487f4508d16ef8987234b6086cb84171634c1a8d06c0aa458c0381511bc23e7",
    "metadata": {},
    "name": "stored-entity",
    "type": "remote/map/base64"
  },
  {
    "customKind": false,
    "reference": "ref://bjp/bcfe9deabed50dca83f7dfcc77c9b133ee5b3a3216a80224d0c0aa26ecd10fdd",
    "metadata": {},
    "name": "stored-entity",
    "type": "remote/map/base64"
  }
]
```

```
${ #stage('Deploy Manifest').context.manifests[0].metadata }

# evaluates to
{
  "name": "nginx",
  "labels": {
    "helm.sh/chart": "nginx-1.0.0",
    "app.kubernetes.io/managed-by": "Helm",
    "app.kubernetes.io/name": "nginx",
    "app.kubernetes.io/instance": "nginx",
    "app.kubernetes.io/version": "1.0.0"
  }
}
```

By accessing some field in the manifest, it will automatically get expanded. Setting `expression.aggressiveExpansion` to false has the biggest memory benefit to Orca.
However, some users may have different use cases where they need the manifest objects all expanded.
Further if users want access to the full manifest, they can instead call the `#fetchReference` SpEL function to retrieve the manifests contents.
