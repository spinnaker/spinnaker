package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import lombok.EqualsAndHashCode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@EqualsAndHashCode
public class KubernetesApiGroup {
  private static final Map<String, KubernetesApiGroup> values = Collections.synchronizedMap(new TreeMap<>(
    String.CASE_INSENSITIVE_ORDER));
  // from https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/
  public static KubernetesApiGroup NONE = new KubernetesApiGroup("");
  public static KubernetesApiGroup CORE = new KubernetesApiGroup("core");
  public static KubernetesApiGroup BATCH = new KubernetesApiGroup("batch");
  public static KubernetesApiGroup APPS = new KubernetesApiGroup("apps");
  public static KubernetesApiGroup EXTENSIONS = new KubernetesApiGroup("extensions");
  public static KubernetesApiGroup STORAGE_K8S_IO = new KubernetesApiGroup("storage.k8s.io");
  public static KubernetesApiGroup APIEXTENSIONS_K8S_IO = new KubernetesApiGroup("apiextensions.k8s.io");
  public static KubernetesApiGroup APIREGISTRATION_K8S_IO = new KubernetesApiGroup("apiregistration.k8s.io");
  public static KubernetesApiGroup AUTOSCALING = new KubernetesApiGroup("autoscaling");
  public static KubernetesApiGroup ADMISSIONREGISTRATION_K8S_IO = new KubernetesApiGroup("admissionregistration.k8s.io");
  public static KubernetesApiGroup POLICY = new KubernetesApiGroup("policy");
  public static KubernetesApiGroup SCHEDULING_K8S_IO = new KubernetesApiGroup("scheduling.k8s.io");
  public static KubernetesApiGroup SETTINGS_K8S_IO = new KubernetesApiGroup("settings.k8s.io");
  public static KubernetesApiGroup AUTHORIZATION_K8S_IO = new KubernetesApiGroup("authorization.k8s.io");
  public static KubernetesApiGroup AUTHENTICATION_K8S_IO = new KubernetesApiGroup("authentication.k8s.io");
  public static KubernetesApiGroup RBAC_AUTHORIZATION_K8S_IO = new KubernetesApiGroup("rbac.authorization.k8s.io");
  public static KubernetesApiGroup CERTIFICATES_K8S_IO = new KubernetesApiGroup("certificates.k8s.io");
  public static KubernetesApiGroup NETWORKING_K8S_IO = new KubernetesApiGroup("networking.k8s.io");


  private final String name;

  // including NONE since it seems like any resource without an api group would have to be native
  private final static ImmutableSet<KubernetesApiGroup> NATIVE_GROUPS = ImmutableSet
    .of(CORE, BATCH, APPS, EXTENSIONS, STORAGE_K8S_IO, APIEXTENSIONS_K8S_IO, APIREGISTRATION_K8S_IO, AUTOSCALING,
      ADMISSIONREGISTRATION_K8S_IO, POLICY, SCHEDULING_K8S_IO, SETTINGS_K8S_IO, AUTHORIZATION_K8S_IO,
      AUTHENTICATION_K8S_IO, RBAC_AUTHORIZATION_K8S_IO, CERTIFICATES_K8S_IO, NETWORKING_K8S_IO, NONE);



  protected KubernetesApiGroup(String name) {
    this.name = name;
    values.put(name, this);
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  public boolean isCustomResourceGroup() {
    return !NATIVE_GROUPS.contains(this);
  }

  @JsonCreator
  public static KubernetesApiGroup fromString(String name) {
    if (StringUtils.isEmpty(name)) {
      return null;
    }

    synchronized (values) {
      return values.computeIfAbsent(name, KubernetesApiGroup::new);
    }
  }
}
