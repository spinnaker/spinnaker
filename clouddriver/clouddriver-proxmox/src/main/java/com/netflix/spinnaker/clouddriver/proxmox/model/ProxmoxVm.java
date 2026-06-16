package com.netflix.spinnaker.clouddriver.proxmox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Proxmox VE QEMU/KVM virtual machine as returned by: GET /api2/json/nodes/{node}/qemu
 * (list – subset of fields) GET /api2/json/nodes/{node}/qemu/{vmid}/status/current (full status)
 * GET /api2/json/nodes/{node}/qemu/{vmid}/config (configuration)
 *
 * <p>Fields from all three endpoints are merged here. Fields absent from a given response will be
 * null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxVm {

  // ── Identity ─────────────────────────────────────────────────────────────

  /** Virtual machine ID (cluster-wide unique integer). */
  @JsonProperty("vmid")
  private Integer vmId;

  /** VM display name. */
  @JsonProperty("name")
  private String name;

  /** Node the VM is running on / located on. */
  @JsonProperty("node")
  private String node;

  /** Resource type – always "qemu". */
  @JsonProperty("type")
  private String type;

  /** Cluster resource ID, e.g. "qemu/101". */
  @JsonProperty("id")
  private String id;

  // ── Runtime status ────────────────────────────────────────────────────────

  /**
   * VM power status: "running", "stopped", "paused", "suspended". From status/current or
   * cluster/resources.
   */
  @JsonProperty("status")
  private String status;

  /** VM uptime in seconds (0 when stopped). */
  @JsonProperty("uptime")
  private Long uptime;

  /** Process ID of the QEMU process (null when stopped). */
  @JsonProperty("pid")
  private Integer pid;

  /** QMP (QEMU Machine Protocol) status string. */
  @JsonProperty("qmpstatus")
  private String qmpStatus;

  /** HA manager state for this VM. */
  @JsonProperty("ha")
  private HaState ha;

  // ── CPU ──────────────────────────────────────────────────────────────────

  /** Current CPU usage as a fraction (0.0 – max_cpu). */
  @JsonProperty("cpu")
  private Double cpu;

  /** Number of virtual CPUs configured. */
  @JsonProperty("cpus")
  private Integer cpus;

  /** CPU type/model string (e.g. "host", "kvm64"). From config. */
  @JsonProperty("cpu_type")
  private String cpuType;

  // ── Memory ───────────────────────────────────────────────────────────────

  /** Memory currently used by the VM (bytes). */
  @JsonProperty("mem")
  private Long mem;

  /** Maximum memory allocated to the VM (bytes). */
  @JsonProperty("maxmem")
  private Long maxMem;

  /** Balloon memory size in bytes (runtime; null if balloon not used). */
  @JsonProperty("balloon")
  private Long balloon;

  /** Balloon target size in bytes. */
  @JsonProperty("balloon_info")
  private BalloonInfo balloonInfo;

  // ── Disk ─────────────────────────────────────────────────────────────────

  /** Root disk usage (bytes) – from cluster/resources. */
  @JsonProperty("disk")
  private Long disk;

  /** Root disk size (bytes) – from cluster/resources. */
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

  // ── Configuration (from /config endpoint) ────────────────────────────────

  /** Boot order string (e.g. "order=scsi0;net0"). */
  @JsonProperty("boot")
  private String boot;

  /** Machine type (e.g. "q35", "pc"). */
  @JsonProperty("machine")
  private String machine;

  /** BIOS type: "seabios" or "ovmf". */
  @JsonProperty("bios")
  private String bios;

  /** OS type string (e.g. "l26" for Linux 2.6+, "win11"). */
  @JsonProperty("ostype")
  private String osType;

  /** Number of CPU sockets. */
  @JsonProperty("sockets")
  private Integer sockets;

  /** Number of cores per socket. */
  @JsonProperty("cores")
  private Integer cores;

  /** SCSI controller model (e.g. "virtio-scsi-pci"). */
  @JsonProperty("scsihw")
  private String scsiHw;

  /** Primary network interface config string (net0). */
  @JsonProperty("net0")
  private String net0;

  /** Primary SCSI disk config string (scsi0). */
  @JsonProperty("scsi0")
  private String scsi0;

  /** IDE2 (typically CD/DVD drive) config string. */
  @JsonProperty("ide2")
  private String ide2;

  /** Cloud-Init drive location (e.g. "local-lvm:vm-101-cloudinit"). */
  @JsonProperty("cloudinit")
  private String cloudInit;

  /** Description / notes field. */
  @JsonProperty("description")
  private String description;

  /** Tags associated with the VM (semicolon-separated). */
  @JsonProperty("tags")
  private String tags;

  /** Whether the VM starts on boot (1 = yes, 0 = no). */
  @JsonProperty("onboot")
  private Integer onBoot;

  /** Protection flag – prevents deletion/modification. */
  @JsonProperty("protection")
  private Integer protection;

  /** Whether the QEMU agent is enabled (1 = yes). */
  @JsonProperty("agent")
  private String agent;

  /** Template flag – 1 if this VM is a template. */
  @JsonProperty("template")
  private Integer template;

  // ── Nested types ─────────────────────────────────────────────────────────

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HaState {
    @JsonProperty("managed")
    private Integer managed;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class BalloonInfo {
    @JsonProperty("actual")
    private Long actual;

    @JsonProperty("max_mem")
    private Long maxMem;
  }
}
