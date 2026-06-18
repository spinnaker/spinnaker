/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.proxmox.client;

import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxLxc;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxStorage;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ProxmoxApiService {

  // ── Read-only endpoints ────────────────────────────────────────────────────

  @GET("nodes")
  Call<ProxmoxResponse<List<ProxmoxNode>>> getNodes();

  @GET("nodes/{node}/qemu")
  Call<ProxmoxResponse<List<ProxmoxVm>>> getVms(@Path("node") String node);

  @GET("nodes/{node}/qemu/{vmid}")
  Call<ProxmoxResponse<ProxmoxVm>> getVmConfig(@Path("node") String node, @Path("vmid") int vmid);

  @GET("nodes/{node}/lxc")
  Call<ProxmoxResponse<List<ProxmoxLxc>>> getContainers(@Path("node") String node);

  @GET("nodes/{node}/storage")
  Call<ProxmoxResponse<List<ProxmoxStorage>>> getStorage(@Path("node") String node);

  // ── Task polling ───────────────────────────────────────────────────────────

  /** Returns the status of a Proxmox background task identified by its UPID. */
  @GET("nodes/{node}/tasks/{upid}/status")
  Call<ProxmoxResponse<ProxmoxTaskStatus>> getTaskStatus(
      @Path("node") String node, @Path("upid") String upid);

  // ── QEMU (KVM) lifecycle ───────────────────────────────────────────────────

  /**
   * Create a new QEMU VM. Returns a UPID to poll for completion.
   *
   * <p>Required params: {@code vmid}, {@code name}. Optional: {@code memory}, {@code cores}, {@code
   * net0}, {@code scsi0}, {@code cdrom}, {@code tags}, etc.
   */
  @FormUrlEncoded
  @POST("nodes/{node}/qemu")
  Call<ProxmoxResponse<String>> createVm(
      @Path("node") String node, @FieldMap Map<String, String> params);

  /** Stop a running QEMU VM. Returns a UPID. */
  @FormUrlEncoded
  @POST("nodes/{node}/qemu/{vmid}/status/stop")
  Call<ProxmoxResponse<String>> stopVm(
      @Path("node") String node, @Path("vmid") int vmid, @FieldMap Map<String, String> params);

  /** Reboot a running QEMU VM. Returns a UPID. */
  @FormUrlEncoded
  @POST("nodes/{node}/qemu/{vmid}/status/reboot")
  Call<ProxmoxResponse<String>> rebootVm(
      @Path("node") String node, @Path("vmid") int vmid, @FieldMap Map<String, String> params);

  /** Delete a QEMU VM. The VM must be stopped first. Returns a UPID. */
  @DELETE("nodes/{node}/qemu/{vmid}")
  Call<ProxmoxResponse<String>> deleteVm(@Path("node") String node, @Path("vmid") int vmid);

  /**
   * Update QEMU VM configuration. May return a UPID for changes that require agent interaction, or
   * null for immediate changes.
   */
  @FormUrlEncoded
  @PUT("nodes/{node}/qemu/{vmid}/config")
  Call<ProxmoxResponse<String>> updateVmConfig(
      @Path("node") String node, @Path("vmid") int vmid, @FieldMap Map<String, String> params);

  // ── LXC (container) lifecycle ──────────────────────────────────────────────

  /**
   * Create a new LXC container. Returns a UPID.
   *
   * <p>Required params: {@code vmid}, {@code ostemplate}, {@code rootfs}. Optional: {@code
   * hostname}, {@code memory}, {@code cores}, {@code net0}, {@code tags}, etc.
   */
  @FormUrlEncoded
  @POST("nodes/{node}/lxc")
  Call<ProxmoxResponse<String>> createLxc(
      @Path("node") String node, @FieldMap Map<String, String> params);

  /** Stop a running LXC container. Returns a UPID. */
  @FormUrlEncoded
  @POST("nodes/{node}/lxc/{vmid}/status/stop")
  Call<ProxmoxResponse<String>> stopLxc(
      @Path("node") String node, @Path("vmid") int vmid, @FieldMap Map<String, String> params);

  /** Reboot a running LXC container. Returns a UPID. */
  @FormUrlEncoded
  @POST("nodes/{node}/lxc/{vmid}/status/reboot")
  Call<ProxmoxResponse<String>> rebootLxc(
      @Path("node") String node, @Path("vmid") int vmid, @FieldMap Map<String, String> params);

  /** Delete an LXC container. The container must be stopped first. Returns a UPID. */
  @DELETE("nodes/{node}/lxc/{vmid}")
  Call<ProxmoxResponse<String>> deleteLxc(@Path("node") String node, @Path("vmid") int vmid);

  /** Update LXC container configuration. May return a UPID or null. */
  @FormUrlEncoded
  @PUT("nodes/{node}/lxc/{vmid}/config")
  Call<ProxmoxResponse<String>> updateLxcConfig(
      @Path("node") String node, @Path("vmid") int vmid, @FieldMap Map<String, String> params);
}
