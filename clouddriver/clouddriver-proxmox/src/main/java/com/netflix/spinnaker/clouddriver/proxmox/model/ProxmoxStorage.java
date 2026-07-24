package com.netflix.spinnaker.clouddriver.proxmox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Proxmox VE storage as returned by: GET /api2/json/nodes/{node}/storage (list per
 * node) GET /api2/json/nodes/{node}/storage/{storage}/status (detailed status) GET
 * /api2/json/cluster/resources?type=storage (cluster-wide)
 *
 * <p>Storage plugin types include: dir, lvm, lvmthin, zfspool, nfs, cifs, glusterfs, rbd (Ceph),
 * pbs (Proxmox Backup Server), btrfs, zfs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxStorage {

  // ── Identity ─────────────────────────────────────────────────────────────

  /** Storage identifier / name (e.g. "local", "local-lvm", "ceph-pool"). */
  @JsonProperty("storage")
  private String storage;

  /** Node this storage entry belongs to. */
  @JsonProperty("node")
  private String node;

  /** Resource type – always "storage" in cluster resources. */
  @JsonProperty("type")
  private String type;

  /** Cluster resource ID, e.g. "storage/pve/local". */
  @JsonProperty("id")
  private String id;

  // ── Plugin / backend ─────────────────────────────────────────────────────

  /**
   * Storage plugin type: "dir", "lvm", "lvmthin", "zfspool", "nfs", "cifs", "rbd", "pbs", "btrfs",
   * "zfs", etc.
   */
  @JsonProperty("plugintype")
  private String pluginType;

  /** Storage backend path (for dir/nfs/cifs plugin types). */
  @JsonProperty("path")
  private String path;

  /** NFS/CIFS server hostname or IP. */
  @JsonProperty("server")
  private String server;

  /** NFS export path or CIFS share name. */
  @JsonProperty("export")
  private String export;

  /** CIFS share name. */
  @JsonProperty("share")
  private String share;

  /** Volume group name (for lvm/lvmthin). */
  @JsonProperty("vgname")
  private String vgName;

  /** Thin pool name (for lvmthin). */
  @JsonProperty("thinpool")
  private String thinPool;

  /** ZFS pool name (for zfspool/zfs). */
  @JsonProperty("pool")
  private String pool;

  /** Ceph monitor list (comma-separated, for rbd). */
  @JsonProperty("monhost")
  private String monHost;

  /** Ceph pool name (for rbd). */
  @JsonProperty("krbd")
  private Integer krbd;

  // ── Status ───────────────────────────────────────────────────────────────

  /** Storage availability: "available", "disabled", "not available". */
  @JsonProperty("status")
  private String status;

  /** Whether the storage is enabled (1 = enabled). */
  @JsonProperty("enabled")
  private Integer enabled;

  /** Whether the storage is shared across all cluster nodes (1 = shared). */
  @JsonProperty("shared")
  private Integer shared;

  // ── Capacity ─────────────────────────────────────────────────────────────

  /** Bytes currently used. */
  @JsonProperty("disk")
  private Long disk;

  /** Total storage capacity (bytes). */
  @JsonProperty("maxdisk")
  private Long maxDisk;

  /** Bytes available (may differ from maxdisk - disk due to overhead). */
  @JsonProperty("avail")
  private Long avail;

  /** Total bytes (synonym for maxdisk, returned by status endpoint). */
  @JsonProperty("total")
  private Long total;

  /** Bytes used (synonym for disk, returned by status endpoint). */
  @JsonProperty("used")
  private Long used;

  /** Used fraction as a float (0.0 – 1.0), returned by status endpoint. */
  @JsonProperty("used_fraction")
  private Double usedFraction;

  // ── Content types supported ───────────────────────────────────────────────

  /**
   * Comma-separated content types this storage can hold. Possible values: images, rootdir, vztmpl,
   * backup, iso, snippets. Example: "images,rootdir"
   */
  @JsonProperty("content")
  private String content;

  // ── Options ──────────────────────────────────────────────────────────────

  /** Whether encryption is configured (for PBS). */
  @JsonProperty("encryption-key")
  private String encryptionKey;

  /** PBS datastore name. */
  @JsonProperty("datastore")
  private String dataStore;

  /** PBS server URL. */
  @JsonProperty("server2")
  private String server2;

  /** Prune options string (e.g. "keep-last=5,keep-weekly=2"). */
  @JsonProperty("prune-backups")
  private String pruneBackups;

  /** Whether thin provisioning is used. */
  @JsonProperty("sparse")
  private Integer sparse;

  /** Mount options string. */
  @JsonProperty("options")
  private String options;

  /** NFS version (e.g. "3", "4", "4.1"). */
  @JsonProperty("vers")
  private String vers;
}
