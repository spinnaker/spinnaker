package com.netflix.spinnaker.clouddriver.proxmox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxResource;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Proxmox VE LXC container as returned by: GET /api2/json/nodes/{node}/lxc (list) GET
 * /api2/json/nodes/{node}/lxc/{vmid}/status/current (runtime status) GET
 * /api2/json/nodes/{node}/lxc/{vmid}/config (configuration)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxLxc implements ProxmoxResource {

  // ── Identity ─────────────────────────────────────────────────────────────

  /** Container ID (cluster-wide unique integer). */
  @JsonProperty("vmid")
  private Integer vmId;

  /** Container hostname. */
  @JsonProperty("name")
  private String name;

  /** Node the container runs on. */
  @JsonProperty("node")
  private String node;

  /** Resource type – always "lxc". */
  @JsonProperty("type")
  private String type;

  /** Cluster resource ID, e.g. "lxc/102". */
  @JsonProperty("id")
  private String id;

  // ── Runtime status ────────────────────────────────────────────────────────

  /** Container status: "running", "stopped". */
  @JsonProperty("status")
  private String status;

  /** Uptime in seconds (0 when stopped). */
  @JsonProperty("uptime")
  private Long uptime;

  // ── CPU ──────────────────────────────────────────────────────────────────

  /** Current CPU usage as a fraction. */
  @JsonProperty("cpu")
  private Double cpu;

  /** Number of CPU cores allocated to the container. */
  @JsonProperty("cpus")
  private Integer cpus;

  /** CPU usage limit (fraction of host CPUs). */
  @JsonProperty("cpulimit")
  private Double cpuLimit;

  /** CPU shares weight (relative to other containers). */
  @JsonProperty("cpuunits")
  private Integer cpuUnits;

  // ── Memory ───────────────────────────────────────────────────────────────

  /** Memory in use (bytes). */
  @JsonProperty("mem")
  private Long mem;

  /** Memory limit (bytes). */
  @JsonProperty("maxmem")
  private Long maxMem;

  /** Swap in use (bytes). */
  @JsonProperty("swap")
  private Long swap;

  /** Swap limit (bytes). */
  @JsonProperty("maxswap")
  private Long maxSwap;

  // ── Disk ─────────────────────────────────────────────────────────────────

  /** Disk used (bytes). */
  @JsonProperty("disk")
  private Long disk;

  /** Disk size / limit (bytes). */
  @JsonProperty("maxdisk")
  private Long maxDisk;

  /** Disk read throughput (bytes/s). */
  @JsonProperty("diskread")
  private Long diskRead;

  /** Disk write throughput (bytes/s). */
  @JsonProperty("diskwrite")
  private Long diskWrite;

  // ── Network ──────────────────────────────────────────────────────────────

  /** Network bytes received. */
  @JsonProperty("netin")
  private Long netIn;

  /** Network bytes sent. */
  @JsonProperty("netout")
  private Long netOut;

  // ── Configuration (from /config) ─────────────────────────────────────────

  /** OS template used to create the container (e.g. "local:vztmpl/debian-12-standard..."). */
  @JsonProperty("ostemplate")
  private String osTemplate;

  /** OS type (e.g. "debian", "ubuntu", "alpine"). */
  @JsonProperty("ostype")
  private String osType;

  /** Container architecture (e.g. "amd64", "arm64"). */
  @JsonProperty("arch")
  private String arch;

  /**
   * Root filesystem configuration string. Format: {@code <storage>:<size>,<options>} e.g.
   * "local-lvm:8".
   */
  /**
   * All disk-like config entries (rootfs, mp*, unused*), keyed by device name. Populated from the
   * /config endpoint by the caching agent.
   */
  private Map<String, String> disks;

  @JsonProperty("rootfs")
  private String rootFs;

  /** Primary network interface config string (net0). */
  @JsonProperty("net0")
  private String net0;

  /** DNS nameserver(s) for the container. */
  @JsonProperty("nameserver")
  private String nameServer;

  /** DNS search domain. */
  @JsonProperty("searchdomain")
  private String searchDomain;

  /** Whether to start the container on host boot (1 = yes). */
  @JsonProperty("onboot")
  private Integer onBoot;

  /** Description / notes. */
  @JsonProperty("description")
  private String description;

  /** Tags (semicolon-separated). */
  @JsonProperty("tags")
  private String tags;

  /** Whether the container runs in privileged mode (1 = privileged). */
  @JsonProperty("unprivileged")
  private Integer unprivileged;

  /** Protection flag – prevents deletion. */
  @JsonProperty("protection")
  private Integer protection;

  /** Number of open file descriptors allowed. */
  @JsonProperty("ulimit")
  private Integer ulimit;

  /** Startup/shutdown order and delays. */
  @JsonProperty("startup")
  private String startup;

  /** Features enabled on the container (e.g. "nesting=1,keyctl=1"). */
  @JsonProperty("features")
  private String features;

  /** Template flag – 1 if this container is a template. */
  @JsonProperty("template")
  private Integer template;
}
