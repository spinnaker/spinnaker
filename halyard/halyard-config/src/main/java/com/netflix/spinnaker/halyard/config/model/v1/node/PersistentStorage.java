package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PersistentStorage extends Node {
  PersistentStore.PersistentStoreType persistentStoreType;
  AzsPersistentStore azs = new AzsPersistentStore();
  GcsPersistentStore gcs = new GcsPersistentStore();
  RedisPersistentStore redis = new RedisPersistentStore();
  S3PersistentStore s3 = new S3PersistentStore();

  @JsonProperty(access = Access.WRITE_ONLY)
  OracleBMCSPersistentStore oraclebmcs = new OracleBMCSPersistentStore();

  OraclePersistentStore oracle = new OraclePersistentStore();

  @Override
  public String getNodeName() {
    return "persistentStorage";
  }

  public PersistentStore.PersistentStoreType getPersistentStoreType() {
    if (persistentStoreType == PersistentStore.PersistentStoreType.ORACLEBMCS) {
      return PersistentStore.PersistentStoreType.ORACLE;
    }
    return persistentStoreType;
  }

  public OraclePersistentStore getOracle() {
    return OraclePersistentStore.mergeOracleBMCSPersistentStore(oracle, oraclebmcs);
  }

  @Override
  public NodeIterator getChildren() {
    List<Node> nodes = new ArrayList<Node>();

    NodeIterator children = NodeIteratorFactory.makeReflectiveIterator(this);
    Node child = children.getNext();
    while (child != null) {
      if (!child.getNodeName().equals("oracle") && !child.getNodeName().equals("oraclebmcs")) {
        nodes.add(child);
      }
      child = children.getNext();
    }

    nodes.add(OraclePersistentStore.mergeOracleBMCSPersistentStore(oracle, oraclebmcs));

    return NodeIteratorFactory.makeListIterator(nodes);
  }

  public static Class<? extends PersistentStore> translatePersistentStoreType(
      String persistentStoreType) {
    Optional<? extends Class<?>> res =
        Arrays.stream(PersistentStorage.class.getDeclaredFields())
            .filter(f -> f.getName().equals(persistentStoreType))
            .map(Field::getType)
            .findFirst();

    if (res.isPresent()) {
      return (Class<? extends PersistentStore>) res.get();
    } else {
      throw new IllegalArgumentException(
          "No persistent store with name \"" + persistentStoreType + "\" handled by halyard.");
    }
  }
}
