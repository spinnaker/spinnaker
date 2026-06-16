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
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ProxmoxApiService {
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
}
