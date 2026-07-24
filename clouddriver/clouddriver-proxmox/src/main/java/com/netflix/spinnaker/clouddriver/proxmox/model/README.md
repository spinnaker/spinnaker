# Proxmox VE API – Java Model Objects

Java model classes for deserialising responses from the [Proxmox VE REST API](https://pve.proxmox.com/pve-docs/api-viewer/).

## Files

| Class | File | API Endpoints |
|---|---|---|
| `ProxmoxNode` | `ProxmoxNode.java` | `GET /nodes`, `GET /nodes/{node}/status` |
| `ProxmoxVm` | `ProxmoxVm.java` | `GET /nodes/{node}/qemu`, `/qemu/{vmid}/status/current`, `/qemu/{vmid}/config` |
| `ProxmoxCluster` | `ProxmoxCluster.java` | `GET /cluster/status`, `GET /cluster/resources` |
| `ProxmoxLxc` | `ProxmoxLxc.java` | `GET /nodes/{node}/lxc`, `/lxc/{vmid}/status/current`, `/lxc/{vmid}/config` |
| `ProxmoxStorage` | `ProxmoxStorage.java` | `GET /nodes/{node}/storage`, `/storage/{storage}/status`, `GET /cluster/resources?type=storage` |
| `ProxmoxApiResponse<T>` | `ProxmoxApiResponse.java` | Generic wrapper for all API responses |

## Dependencies

```xml
<!-- Jackson (Maven) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

```gradle
// Gradle
implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
```

## Usage Examples

### Deserialise a node list

```java
ObjectMapper mapper = new ObjectMapper();

// Raw JSON: { "data": [ {...}, {...} ] }
ProxmoxApiResponse<List<ProxmoxNode>> response =
    mapper.readValue(json, new TypeReference<>() {});

List<ProxmoxNode> nodes = response.getData();
nodes.forEach(n -> System.out.println(n.getNode() + " – " + n.getStatus()));
```

### Deserialise a single VM status

```java
// GET /api2/json/nodes/pve/qemu/101/status/current
ProxmoxApiResponse<ProxmoxVm> response =
    mapper.readValue(json, new TypeReference<>() {});

ProxmoxVm vm = response.getData();
System.out.printf("VM %d (%s): %s%n", vm.getVmId(), vm.getName(), vm.getStatus());
```

### Deserialise cluster resources (mixed types)

```java
// GET /api2/json/cluster/resources
// Returns a flat list of all nodes, VMs, LXCs, and storage.
ProxmoxApiResponse<List<ProxmoxCluster.ClusterResource>> response =
    mapper.readValue(json, new TypeReference<>() {});

response.getData().stream()
    .filter(r -> "qemu".equals(r.getType()))
    .forEach(r -> System.out.println("VM: " + r.getName() + " on " + r.getNode()));
```

### Deserialise LXC container list

```java
// GET /api2/json/nodes/pve/lxc
ProxmoxApiResponse<List<ProxmoxLxc>> response =
    mapper.readValue(json, new TypeReference<>() {});

response.getData().forEach(c ->
    System.out.printf("CT %d (%s): cpu=%.2f mem=%d/%d%n",
        c.getVmId(), c.getName(), c.getCpu(), c.getMem(), c.getMaxMem()));
```

### Deserialise storage status

```java
// GET /api2/json/nodes/pve/storage/local/status
ProxmoxApiResponse<ProxmoxStorage> response =
    mapper.readValue(json, new TypeReference<>() {});

ProxmoxStorage s = response.getData();
System.out.printf("%s (%s): %d / %d bytes%n",
    s.getStorage(), s.getPluginType(), s.getDisk(), s.getMaxDisk());
```

### Use the builder pattern

```java
ProxmoxVm vm = ProxmoxVm.builder()
    .vmId(101)
    .name("web-server")
    .node("pve")
    .status("running")
    .cpus(4)
    .maxMem(8L * 1024 * 1024 * 1024)
    .build();
```

## Notes

- All classes use `@JsonIgnoreProperties(ignoreUnknown = true)` so new API fields added in future Proxmox versions will not break deserialization.
- Fields absent from a given endpoint's response will be `null` (use null-checks or `Optional`).
- Proxmox uses `0`/`1` integers for boolean flags (e.g. `onboot`, `shared`, `unprivileged`). Helper wrappers can be added if preferred.
- The `/nodes/{node}/qemu/{vmid}/config` endpoint returns disk and network interface keys dynamically (e.g. `scsi1`, `net1`). Only `scsi0`, `net0`, and `ide2` are modelled here; add additional fields as needed or capture them with a `@JsonAnySetter` map.

## Key API Base URL

```
https://<host>:8006/api2/json/
```
