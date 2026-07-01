package com.netflix.spinnaker.clouddriver.proxmox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the Proxmox VE cluster as returned by: GET /api2/json/cluster/status
 *
 * <p>The response is a JSON array of mixed-type entries (one "cluster" record and one "node" record
 * per cluster member). This class models the "cluster" entry specifically.
 *
 * <p>The inner {@link ClusterResource} class models entries from: GET /api2/json/cluster/resources
 * which lists every resource (node, vm, lxc, storage) across the cluster.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxCluster {

  // ── Identity ─────────────────────────────────────────────────────────────

  /** Cluster name as configured. */
  @JsonProperty("name")
  private String name;

  /** Entry type – "cluster" for the cluster-level record. */
  @JsonProperty("type")
  private String type;

  // ── Cluster health ───────────────────────────────────────────────────────

  /** 1 if cluster is quorate (has quorum), 0 otherwise. */
  @JsonProperty("quorate")
  private Integer quorate;

  /** Total number of nodes in the cluster. */
  @JsonProperty("nodes")
  private Integer nodes;

  /** Number of nodes currently online. */
  @JsonProperty("nodes_online")
  private Integer nodesOnline;

  /** Corosync cluster version / config version. */
  @JsonProperty("version")
  private Integer version;

  /** Corosync configuration ID. */
  @JsonProperty("id")
  private String id;

  // ── All cluster resources ─────────────────────────────────────────────────

  /**
   * Full list of resources from GET /api2/json/cluster/resources. Populated when fetching
   * cluster-wide resource data; null otherwise.
   */
  private List<ClusterResource> resources;

  // ── Nested: generic cluster resource ─────────────────────────────────────

  /**
   * A single entry from GET /api2/json/cluster/resources. The {@code type} field distinguishes
   * nodes, VMs, LXCs, and storage.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ClusterResource {

    /** Resource type: "node", "qemu", "lxc", "storage", "pool". */
    @JsonProperty("type")
    private String type;

    /** Unique resource ID, e.g. "qemu/101", "node/pve", "storage/pve/local". */
    @JsonProperty("id")
    private String id;

    /** Node that owns/hosts this resource. */
    @JsonProperty("node")
    private String node;

    /** Resource name / hostname. */
    @JsonProperty("name")
    private String name;

    /** Status string (e.g. "running", "stopped", "online", "available"). */
    @JsonProperty("status")
    private String status;

    /** VM/CT ID (for qemu and lxc resources). */
    @JsonProperty("vmid")
    private Integer vmId;

    /** Storage name (for storage resources). */
    @JsonProperty("storage")
    private String storage;

    /** Pool name (for pool resources). */
    @JsonProperty("pool")
    private String pool;

    // ── Shared resource metrics ───────────────────────────────────────────

    @JsonProperty("cpu")
    private Double cpu;

    @JsonProperty("maxcpu")
    private Integer maxCpu;

    @JsonProperty("mem")
    private Long mem;

    @JsonProperty("maxmem")
    private Long maxMem;

    @JsonProperty("disk")
    private Long disk;

    @JsonProperty("maxdisk")
    private Long maxDisk;

    @JsonProperty("uptime")
    private Long uptime;

    @JsonProperty("netin")
    private Long netIn;

    @JsonProperty("netout")
    private Long netOut;

    @JsonProperty("diskread")
    private Long diskRead;

    @JsonProperty("diskwrite")
    private Long diskWrite;

    /** HA state flags. */
    @JsonProperty("hastate")
    private String haState;

    /** Storage plugin type (e.g. "dir", "lvm", "zfspool", "rbd"). */
    @JsonProperty("plugintype")
    private String pluginType;

    /** Whether the storage is shared across nodes (1 = shared). */
    @JsonProperty("shared")
    private Integer shared;
  }
}
