package com.netflix.spinnaker.clouddriver.proxmox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Proxmox VE node as returned by: GET /api2/json/nodes GET
 * /api2/json/nodes/{node}/status
 *
 * <p>The /nodes list returns a subset; /nodes/{node}/status includes deeper memory, rootfs, swap,
 * and CPU detail sub-objects.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxNode {

  // ── Core identity ────────────────────────────────────────────────────────

  /** Node hostname (e.g. "pve", "node2"). */
  @JsonProperty("node")
  private String node;

  /** Node status: "online", "offline", "unknown". */
  @JsonProperty("status")
  private String status;

  /** Node type – always "node" in cluster resources. */
  @JsonProperty("type")
  private String type;

  /** Unique resource ID in the cluster, e.g. "node/pve". */
  @JsonProperty("id")
  private String id;

  // ── CPU ──────────────────────────────────────────────────────────────────

  /** Current CPU utilisation as a fraction (0.0 – 1.0). */
  @JsonProperty("cpu")
  private Double cpu;

  /** Maximum number of CPUs / cores available on this node. */
  @JsonProperty("maxcpu")
  private Integer maxCpu;

  // ── Memory ───────────────────────────────────────────────────────────────

  /** Memory currently in use (bytes). */
  @JsonProperty("mem")
  private Long mem;

  /** Total memory available (bytes). */
  @JsonProperty("maxmem")
  private Long maxMem;

  /** Detailed memory breakdown – populated by /nodes/{node}/status. */
  @JsonProperty("memory")
  private MemoryInfo memory;

  // ── Swap ─────────────────────────────────────────────────────────────────

  /** Swap in use (bytes). */
  @JsonProperty("swap")
  private Long swap;

  /** Total swap available (bytes). */
  @JsonProperty("maxswap")
  private Long maxSwap;

  // ── Disk / root filesystem ────────────────────────────────────────────────

  /** Root filesystem used (bytes). */
  @JsonProperty("disk")
  private Long disk;

  /** Root filesystem total size (bytes). */
  @JsonProperty("maxdisk")
  private Long maxDisk;

  /** Detailed root-fs info – populated by /nodes/{node}/status. */
  @JsonProperty("rootfs")
  private DiskInfo rootFs;

  // ── Network / uptime ─────────────────────────────────────────────────────

  /** Node uptime in seconds. */
  @JsonProperty("uptime")
  private Long uptime;

  /** SSL fingerprint of the node certificate. */
  @JsonProperty("ssl_fingerprint")
  private String sslFingerprint;

  /** Proxmox VE version string (e.g. "8.2.2"). */
  @JsonProperty("pveversion")
  private String pveVersion;

  /** Kernel version string. */
  @JsonProperty("kversion")
  private String kVersion;

  // ── Nested types ─────────────────────────────────────────────────────────

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MemoryInfo {
    @JsonProperty("used")
    private Long used;

    @JsonProperty("free")
    private Long free;

    @JsonProperty("total")
    private Long total;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DiskInfo {
    @JsonProperty("used")
    private Long used;

    @JsonProperty("free")
    private Long free;

    @JsonProperty("total")
    private Long total;

    @JsonProperty("avail")
    private Long avail;
  }
}
