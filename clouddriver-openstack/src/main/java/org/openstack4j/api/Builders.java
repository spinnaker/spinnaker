/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openstack4j.api;

import org.openstack4j.model.common.builder.LinkBuilder;
import org.openstack4j.model.compute.builder.BlockDeviceMappingBuilder;
import org.openstack4j.model.compute.builder.ComputeBuilders;
import org.openstack4j.model.compute.builder.FlavorBuilder;
import org.openstack4j.model.compute.builder.FloatingIPBuilder;
import org.openstack4j.model.compute.builder.QuotaSetUpdateBuilder;
import org.openstack4j.model.compute.builder.SecurityGroupRuleBuilder;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;
import org.openstack4j.model.gbp.builder.ExternalPolicyBuilder;
import org.openstack4j.model.gbp.builder.ExternalRoutesBuilder;
import org.openstack4j.model.gbp.builder.ExternalSegmentBuilder;
import org.openstack4j.model.gbp.builder.L2PolicyBuilder;
import org.openstack4j.model.gbp.builder.L3PolicyBuilder;
import org.openstack4j.model.gbp.builder.NatPoolBuilder;
import org.openstack4j.model.gbp.builder.NetworkServicePolicyBuilder;
import org.openstack4j.model.gbp.builder.PolicyActionCreateBuilder;
import org.openstack4j.model.gbp.builder.PolicyActionUpdateBuilder;
import org.openstack4j.model.gbp.builder.PolicyClassifierBuilder;
import org.openstack4j.model.gbp.builder.PolicyClassifierUpdateBuilder;
import org.openstack4j.model.gbp.builder.PolicyRuleBuilder;
import org.openstack4j.model.gbp.builder.PolicyRuleSetBuilder;
import org.openstack4j.model.gbp.builder.PolicyTargetBuilder;
import org.openstack4j.model.gbp.builder.PolicyTargetGroupBuilder;
import org.openstack4j.model.heat.ResourceHealth;
import org.openstack4j.model.heat.SoftwareConfig;
import org.openstack4j.model.heat.StackCreate;
import org.openstack4j.model.heat.StackUpdate;
import org.openstack4j.model.heat.Template;
import org.openstack4j.model.heat.builder.OrchestrationBuilders;
import org.openstack4j.model.heat.builder.ResourceHealthBuilder;
import org.openstack4j.model.heat.builder.SoftwareConfigBuilder;
import org.openstack4j.model.heat.builder.StackCreateBuilder;
import org.openstack4j.model.heat.builder.StackUpdateBuilder;
import org.openstack4j.model.heat.builder.TemplateBuilder;
import org.openstack4j.model.identity.v2.builder.IdentityV2Builders;
import org.openstack4j.model.identity.v3.builder.CredentialBuilder;
import org.openstack4j.model.identity.v3.builder.DomainBuilder;
import org.openstack4j.model.identity.v3.builder.EndpointBuilder;
import org.openstack4j.model.identity.v3.builder.GroupBuilder;
import org.openstack4j.model.identity.v3.builder.IdentityV3Builders;
import org.openstack4j.model.identity.v3.builder.PolicyBuilder;
import org.openstack4j.model.identity.v3.builder.ProjectBuilder;
import org.openstack4j.model.identity.v3.builder.RegionBuilder;
import org.openstack4j.model.identity.v3.builder.RoleBuilder;
import org.openstack4j.model.identity.v3.builder.ServiceBuilder;
import org.openstack4j.model.identity.v3.builder.UserBuilder;
import org.openstack4j.model.image.builder.ImageBuilder;
import org.openstack4j.model.manila.builder.SecurityServiceCreateBuilder;
import org.openstack4j.model.manila.builder.ShareCreateBuilder;
import org.openstack4j.model.manila.builder.ShareManageBuilder;
import org.openstack4j.model.manila.builder.ShareNetworkCreateBuilder;
import org.openstack4j.model.manila.builder.ShareSnapshotCreateBuilder;
import org.openstack4j.model.manila.builder.ShareTypeCreateBuilder;
import org.openstack4j.model.manila.builder.SharedFileSystemBuilders;
import org.openstack4j.model.network.builder.ExtraDhcpOptBuilder;
import org.openstack4j.model.network.builder.NetFloatingIPBuilder;
import org.openstack4j.model.network.builder.NetQuotaBuilder;
import org.openstack4j.model.network.builder.NetSecurityGroupBuilder;
import org.openstack4j.model.network.builder.NetSecurityGroupRuleBuilder;
import org.openstack4j.model.network.builder.NetworkBuilder;
import org.openstack4j.model.network.builder.NetworkBuilders;
import org.openstack4j.model.network.builder.NetworkUpdateBuilder;
import org.openstack4j.model.network.builder.PortBuilder;
import org.openstack4j.model.network.builder.RouterBuilder;
import org.openstack4j.model.network.builder.SubnetBuilder;
import org.openstack4j.model.network.ext.builder.FirewallBuilder;
import org.openstack4j.model.network.ext.builder.FirewallPolicyBuilder;
import org.openstack4j.model.network.ext.builder.FirewallPolicyUpdateBuilder;
import org.openstack4j.model.network.ext.builder.FirewallRuleBuilder;
import org.openstack4j.model.network.ext.builder.FirewallRuleUpdateBuilder;
import org.openstack4j.model.network.ext.builder.FirewallUpdateBuilder;
import org.openstack4j.model.network.ext.builder.HealthMonitorAssociateBuilder;
import org.openstack4j.model.network.ext.builder.HealthMonitorBuilder;
import org.openstack4j.model.network.ext.builder.HealthMonitorUpdateBuilder;
import org.openstack4j.model.network.ext.builder.HealthMonitorV2Builder;
import org.openstack4j.model.network.ext.builder.HealthMonitorV2UpdateBuilder;
import org.openstack4j.model.network.ext.builder.LbPoolBuilder;
import org.openstack4j.model.network.ext.builder.LbPoolUpdateBuilder;
import org.openstack4j.model.network.ext.builder.LbPoolV2Builder;
import org.openstack4j.model.network.ext.builder.LbPoolV2UpdateBuilder;
import org.openstack4j.model.network.ext.builder.ListenerV2Builder;
import org.openstack4j.model.network.ext.builder.ListenerV2UpdateBuilder;
import org.openstack4j.model.network.ext.builder.LoadBalancerV2Builder;
import org.openstack4j.model.network.ext.builder.LoadBalancerV2UpdateBuilder;
import org.openstack4j.model.network.ext.builder.MemberBuilder;
import org.openstack4j.model.network.ext.builder.MemberUpdateBuilder;
import org.openstack4j.model.network.ext.builder.MemberV2Builder;
import org.openstack4j.model.network.ext.builder.MemberV2UpdateBuilder;
import org.openstack4j.model.network.ext.builder.SessionPersistenceBuilder;
import org.openstack4j.model.network.ext.builder.VipBuilder;
import org.openstack4j.model.network.ext.builder.VipUpdateBuilder;
import org.openstack4j.model.sahara.builder.ClusterBuilder;
import org.openstack4j.model.sahara.builder.ClusterTemplateBuilder;
import org.openstack4j.model.sahara.builder.DataProcessingBuilders;
import org.openstack4j.model.sahara.builder.DataSourceBuilder;
import org.openstack4j.model.sahara.builder.JobBinaryBuilder;
import org.openstack4j.model.sahara.builder.JobBuilder;
import org.openstack4j.model.sahara.builder.JobConfigBuilder;
import org.openstack4j.model.sahara.builder.JobExecutionBuilder;
import org.openstack4j.model.sahara.builder.NodeGroupBuilder;
import org.openstack4j.model.sahara.builder.NodeGroupTemplateBuilder;
import org.openstack4j.model.sahara.builder.ServiceConfigBuilder;
import org.openstack4j.model.storage.block.builder.BlockQuotaSetBuilder;
import org.openstack4j.model.storage.block.builder.StorageBuilders;
import org.openstack4j.model.storage.block.builder.VolumeBuilder;
import org.openstack4j.model.storage.block.builder.VolumeSnapshotBuilder;
import org.openstack4j.model.telemetry.builder.AlarmBuilder;
import org.openstack4j.model.telemetry.builder.TelemetryBuilders;
import org.openstack4j.openstack.common.GenericLink;
import org.openstack4j.openstack.compute.builder.NovaBuilders;
import org.openstack4j.openstack.compute.domain.NovaBlockDeviceMappingCreate;
import org.openstack4j.openstack.compute.domain.NovaFlavor;
import org.openstack4j.openstack.compute.domain.NovaFloatingIP;
import org.openstack4j.openstack.compute.domain.NovaQuotaSetUpdate;
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension.SecurityGroupRule;
import org.openstack4j.openstack.compute.domain.NovaServerCreate;
import org.openstack4j.openstack.gbp.domain.GbpExternalPolicyCreate;
import org.openstack4j.openstack.gbp.domain.GbpExternalRoutes;
import org.openstack4j.openstack.gbp.domain.GbpExternalSegment;
import org.openstack4j.openstack.gbp.domain.GbpL2Policy;
import org.openstack4j.openstack.gbp.domain.GbpL3Policy;
import org.openstack4j.openstack.gbp.domain.GbpNatPool;
import org.openstack4j.openstack.gbp.domain.GbpNetworkServicePolicy;
import org.openstack4j.openstack.gbp.domain.GbpPolicyAction;
import org.openstack4j.openstack.gbp.domain.GbpPolicyActionUpdate;
import org.openstack4j.openstack.gbp.domain.GbpPolicyClassifier;
import org.openstack4j.openstack.gbp.domain.GbpPolicyClassifierUpdate;
import org.openstack4j.openstack.gbp.domain.GbpPolicyRule;
import org.openstack4j.openstack.gbp.domain.GbpPolicyRuleSet;
import org.openstack4j.openstack.gbp.domain.GbpPolicyTarget;
import org.openstack4j.openstack.gbp.domain.GbpPolicyTargetGroupCreate;
import org.openstack4j.openstack.heat.builder.HeatBuilders;
import org.openstack4j.openstack.heat.domain.HeatResourceHealth;
import org.openstack4j.openstack.heat.domain.HeatSoftwareConfig;
import org.openstack4j.openstack.heat.domain.HeatStackCreate;
import org.openstack4j.openstack.heat.domain.HeatStackUpdate;
import org.openstack4j.openstack.heat.domain.HeatTemplate;
import org.openstack4j.openstack.identity.v2.builder.KeystoneV2Builders;
import org.openstack4j.openstack.identity.v3.builder.KeystoneV3Builders;
import org.openstack4j.openstack.identity.v3.domain.KeystoneCredential;
import org.openstack4j.openstack.identity.v3.domain.KeystoneDomain;
import org.openstack4j.openstack.identity.v3.domain.KeystoneEndpoint;
import org.openstack4j.openstack.identity.v3.domain.KeystoneGroup;
import org.openstack4j.openstack.identity.v3.domain.KeystonePolicy;
import org.openstack4j.openstack.identity.v3.domain.KeystoneProject;
import org.openstack4j.openstack.identity.v3.domain.KeystoneRegion;
import org.openstack4j.openstack.identity.v3.domain.KeystoneRole;
import org.openstack4j.openstack.identity.v3.domain.KeystoneService;
import org.openstack4j.openstack.identity.v3.domain.KeystoneUser;
import org.openstack4j.openstack.image.domain.GlanceImage;
import org.openstack4j.openstack.manila.builder.ManilaBuilders;
import org.openstack4j.openstack.manila.domain.ManilaSecurityServiceCreate;
import org.openstack4j.openstack.manila.domain.ManilaShareCreate;
import org.openstack4j.openstack.manila.domain.ManilaShareManage;
import org.openstack4j.openstack.manila.domain.ManilaShareNetworkCreate;
import org.openstack4j.openstack.manila.domain.ManilaShareSnapshotCreate;
import org.openstack4j.openstack.manila.domain.ManilaShareTypeCreate;
import org.openstack4j.openstack.networking.builder.NeutronBuilders;
import org.openstack4j.openstack.networking.domain.NeutronExtraDhcpOptCreate;
import org.openstack4j.openstack.networking.domain.NeutronFloatingIP;
import org.openstack4j.openstack.networking.domain.NeutronNetQuota;
import org.openstack4j.openstack.networking.domain.NeutronNetwork;
import org.openstack4j.openstack.networking.domain.NeutronNetworkUpdate;
import org.openstack4j.openstack.networking.domain.NeutronPort;
import org.openstack4j.openstack.networking.domain.NeutronRouter;
import org.openstack4j.openstack.networking.domain.NeutronSecurityGroup;
import org.openstack4j.openstack.networking.domain.NeutronSecurityGroupRule;
import org.openstack4j.openstack.networking.domain.NeutronSubnet;
import org.openstack4j.openstack.networking.domain.ext.NeutronFirewall;
import org.openstack4j.openstack.networking.domain.ext.NeutronFirewallPolicy;
import org.openstack4j.openstack.networking.domain.ext.NeutronFirewallPolicyUpdate;
import org.openstack4j.openstack.networking.domain.ext.NeutronFirewallRule;
import org.openstack4j.openstack.networking.domain.ext.NeutronFirewallRuleUpdate;
import org.openstack4j.openstack.networking.domain.ext.NeutronFirewallUpdate;
import org.openstack4j.openstack.networking.domain.ext.NeutronHealthMonitor;
import org.openstack4j.openstack.networking.domain.ext.NeutronHealthMonitorAssociate;
import org.openstack4j.openstack.networking.domain.ext.NeutronHealthMonitorUpdate;
import org.openstack4j.openstack.networking.domain.ext.NeutronHealthMonitorV2;
import org.openstack4j.openstack.networking.domain.ext.NeutronHealthMonitorV2Update;
import org.openstack4j.openstack.networking.domain.ext.NeutronLbPool;
import org.openstack4j.openstack.networking.domain.ext.NeutronLbPoolUpdate;
import org.openstack4j.openstack.networking.domain.ext.NeutronLbPoolV2;
import org.openstack4j.openstack.networking.domain.ext.NeutronLbPoolV2Update;
import org.openstack4j.openstack.networking.domain.ext.NeutronListenerV2;
import org.openstack4j.openstack.networking.domain.ext.NeutronListenerV2Update;
import org.openstack4j.openstack.networking.domain.ext.NeutronLoadBalancerV2;
import org.openstack4j.openstack.networking.domain.ext.NeutronLoadBalancerV2Update;
import org.openstack4j.openstack.networking.domain.ext.NeutronMember;
import org.openstack4j.openstack.networking.domain.ext.NeutronMemberUpdate;
import org.openstack4j.openstack.networking.domain.ext.NeutronMemberV2;
import org.openstack4j.openstack.networking.domain.ext.NeutronMemberV2Update;
import org.openstack4j.openstack.networking.domain.ext.NeutronSessionPersistence;
import org.openstack4j.openstack.networking.domain.ext.NeutronVip;
import org.openstack4j.openstack.networking.domain.ext.NeutronVipUpdate;
import org.openstack4j.openstack.sahara.builder.SaharaBuilders;
import org.openstack4j.openstack.sahara.domain.SaharaCluster;
import org.openstack4j.openstack.sahara.domain.SaharaClusterTemplate;
import org.openstack4j.openstack.sahara.domain.SaharaDataSource;
import org.openstack4j.openstack.sahara.domain.SaharaJob;
import org.openstack4j.openstack.sahara.domain.SaharaJobBinary;
import org.openstack4j.openstack.sahara.domain.SaharaJobConfig;
import org.openstack4j.openstack.sahara.domain.SaharaJobExecution;
import org.openstack4j.openstack.sahara.domain.SaharaNodeGroup;
import org.openstack4j.openstack.sahara.domain.SaharaNodeGroupTemplate;
import org.openstack4j.openstack.sahara.domain.SaharaServiceConfig;
import org.openstack4j.openstack.storage.block.builder.CinderBuilders;
import org.openstack4j.openstack.storage.block.domain.CinderBlockQuotaSet;
import org.openstack4j.openstack.storage.block.domain.CinderVolume;
import org.openstack4j.openstack.storage.block.domain.CinderVolumeSnapshot;
import org.openstack4j.openstack.telemetry.builder.CeilometerBuilders;
import org.openstack4j.openstack.telemetry.domain.CeilometerAlarm;

/**
 * TODO remove once openstack4j 3.0.3 is released
 * A utility class to quickly access available Builders within the OpenStack API
 *
 * @author Jeremy Unruh
 */
public class Builders {

    /**
     * The builder to create a Link
     *
     * @return the link builder
     */
    public static LinkBuilder link() {
        return GenericLink.builder();
    }

    /**
     * The builder to create a Server
     *
     * @return the server create builder
     */
    public static ServerCreateBuilder server() {
        return NovaServerCreate.builder();
    }

    public static BlockDeviceMappingBuilder blockDeviceMapping() {
        return NovaBlockDeviceMappingCreate.builder();
    }

    public static ExtraDhcpOptBuilder extraDhcpOpt() {
        return NeutronExtraDhcpOptCreate.builder();
    }

    /**
     * The builder to create a Flavor.
     *
     * @return the flavor builder
     */
    public static FlavorBuilder flavor() {
        return NovaFlavor.builder();
    }

    /**
     * The builder to create a Network
     *
     * @return the network builder
     */
    public static NetworkBuilder network() {
        return NeutronNetwork.builder();
    }

    /**
     * The builder to create a Subnet
     *
     * @return the subnet builder
     */
    public static SubnetBuilder subnet() {
        return NeutronSubnet.builder();
    }

    /**
     * The builder to create a Port
     *
     * @return the port builder
     */
    public static PortBuilder port() {
        return NeutronPort.builder();
    }

    /**
     * The builder to create a Router
     *
     * @return the router builder
     */
    public static RouterBuilder router() {
        return NeutronRouter.builder();
    }

    /**
     * The builder to create a Glance Image
     *
     * @return the image builder
     */
    public static ImageBuilder image() {
        return GlanceImage.builder();
    }

    /**
     * The builder to create a Block Volume
     *
     * @return the volume builder
     */
    public static VolumeBuilder volume() {
        return CinderVolume.builder();
    }

    /**
     * The builder to create a Block Volume Snapshot
     *
     * @return the snapshot builder
     */
    public static VolumeSnapshotBuilder volumeSnapshot() {
        return CinderVolumeSnapshot.builder();
    }

    /**
     * The builder to create a Compute/Nova Floating IP
     *
     * @return the floating ip builder
     */
    public static FloatingIPBuilder floatingIP() {
        return NovaFloatingIP.builder();
    }

    /**
     * A Builder which creates a Security Group Rule
     *
     * @return the security group rule builder
     */
    public static SecurityGroupRuleBuilder secGroupRule() {
        return SecurityGroupRule.builder();
    }

    /**
     * The builder to create a Neutron Security Group
     *
     * @return the security group builder
     */
    public static NetSecurityGroupBuilder securityGroup() {
        return NeutronSecurityGroup.builder();
    }

    /**
     * The builder to create a Neutron Security Group Rule
     *
     * @return the security group builder
     */
    public static NetSecurityGroupRuleBuilder securityGroupRule() {
        return NeutronSecurityGroupRule.builder();
    }

    /**
     * The builder to create a Neutron Floating IP Address
     *
     * @return the floating ip builder
     */
    public static NetFloatingIPBuilder netFloatingIP() {
        return NeutronFloatingIP.builder();
    }

    /**
     * The builder to create a {@link Template}
     *
     * @return the TemplateBuilder
     */
    public static TemplateBuilder template() {
        return HeatTemplate.build();
    }

    /**
     * The builder to create a {@link StackCreate}
     *
     * @return the StackCreate builder
     */
    public static StackCreateBuilder stack() {
        return HeatStackCreate.build();
    }

    /**
     * The builder to create a {@link SoftwareConfig}
     *
     * @return the software config builder
     */
    public static SoftwareConfigBuilder softwareConfig() {
        return new HeatSoftwareConfig.Builder();
    }

    /**
     * The builder to create a {@link StackUpdate}
     *
     * @return the StackUpdate builder
     */
    public static StackUpdateBuilder stackUpdate() {
        return HeatStackUpdate.builder();
    }

    /**
     * The builder to create a {@link ResourceHealth}
     * @return
     */
    public static ResourceHealthBuilder resourceHealth() {
        return HeatResourceHealth.builder();
    }

    /**
     * The builder to create NetQuota entities
     *
     * @return the NetQuota builder
     */
    public static NetQuotaBuilder netQuota() {
        return NeutronNetQuota.builder();
    }

    /**
     * The builder to update a network
     *
     * @return the NetworkUpdateBuilder
     */
    public static NetworkUpdateBuilder networkUpdate() {
        return NeutronNetworkUpdate.builder();
    }

    /**
     * The builder to create a lb member
     *
     * @return the Member Builder
     */
    public static MemberBuilder member() {
        return NeutronMember.builder();
    }

    /**
     * The builder to update a lb member
     *
     * @return the MemberUpdate Builder
     */
    public static MemberUpdateBuilder memberUpdate() {
        return NeutronMemberUpdate.builder();
    }

    /**
     * The builder to create and update a sessionPersistence
     *
     * @return SessionPersistenceBuilder
     */
    public static SessionPersistenceBuilder sessionPersistence() {
        return NeutronSessionPersistence.builder();
    }

    /**
     * The builder to create a vip.
     *
     * @return VipBuilder the vip builder
     */
    public static VipBuilder vip() {
        return NeutronVip.builder();
    }

    /**
     * The builder to update a vip.
     *
     * @return VipUpdateBuilder
     */
    public static VipUpdateBuilder vipUpdate() {
        return NeutronVipUpdate.builder();
    }

    /**
     * The builder to create a healthMonitor
     *
     * @return HealthMonitorBuilder
     */
    public static HealthMonitorBuilder healthMonitor() {
        return NeutronHealthMonitor.builder();
    }

    /**
     * The builder to update a healthMonitor
     *
     * @return HealthMonitorUpdateBuilder
     */
    public static HealthMonitorUpdateBuilder healthMonitorUpdate() {
        return NeutronHealthMonitorUpdate.builder();
    }

    /**
     * The builder to create a firewall
     *
     * @return FirewallBuilder
     */
    public static FirewallBuilder firewall() {
        return NeutronFirewall.builder();
    }

    /**
     * The builder to update a healthMonitor
     *
     * @return FirewallUpdateBuilder
     */
    public static FirewallUpdateBuilder firewallUpdate() {
        return NeutronFirewallUpdate.builder();
    }

    /**
     * The builder to create a firewallRule
     *
     * @return FirewallRuleBuilder
     */
    public static FirewallRuleBuilder firewallRule() {
        return NeutronFirewallRule.builder();
    }

    /**
     * The builder to update a firewallRule
     *
     * @return FirewallUpdateBuilder
     */
    public static FirewallRuleUpdateBuilder firewallRuleUpdate() {
        return NeutronFirewallRuleUpdate.builder();
    }

    /**
     * The builder to create a firewallPolicy
     *
     * @return FirewallPolicyBuilder
     */
    public static FirewallPolicyBuilder firewallPolicy() {
        return NeutronFirewallPolicy.builder();
    }

    /**
     * The builder to update a firewallPolicy
     *
     * @return FirewallPolicyUpdateBuilder
     */
    public static FirewallPolicyUpdateBuilder firewallPolicyUpdate() {
        return NeutronFirewallPolicyUpdate.builder();
    }

    /**
     * The builder to create a lbPool
     *
     * @return LbPoolBuilder
     */
    public static LbPoolBuilder lbPool() {
        return NeutronLbPool.builder();
    }

    /**
     * The builder to update a lbPool
     *
     * @return LbPoolUpdateBuilder
     */
    public static LbPoolUpdateBuilder lbPoolUpdate() {
        return NeutronLbPoolUpdate.builder();
    }

    /**
     * The builder to create a lbPool
     *
     * @return HealthMonitorAssociateBuilder
     */
    public static HealthMonitorAssociateBuilder lbPoolAssociateHealthMonitor() {
        return NeutronHealthMonitorAssociate.builder();
    }

    /**
     * The builder to create a sahara cluster
     *
     * @return the cluster builder
     */
    public static ClusterBuilder cluster() {
        return SaharaCluster.builder();
    }

    /**
     * The builder to create a sahara cluster template
     *
     * @return the cluster template builder
     */
    public static ClusterTemplateBuilder clusterTemplate() {
        return SaharaClusterTemplate.builder();
    }

    /**
     * The builder to create a sahara node group
     *
     * @return the node group builder
     */
    public static NodeGroupBuilder nodeGroup() {
        return SaharaNodeGroup.builder();
    }

    /**
     * The builder to create a sahara node group template
     *
     * @return the node group template builder
     */
    public static NodeGroupTemplateBuilder nodeGroupTemplate() {
        return SaharaNodeGroupTemplate.builder();
    }

    /**
     * The builder to create a sahara service configuration
     *
     * @return the service configuration builder
     */
    public static ServiceConfigBuilder serviceConfig() {
        return SaharaServiceConfig.builder();
    }

    /**
     * This builder which creates a QuotaSet for updates
     *
     * @return the QuotaSet update builder
     */
    public static QuotaSetUpdateBuilder quotaSet() {
        return NovaQuotaSetUpdate.builder();
    }

    /**
     * The builder to create an Alarm
     *
     * @return the image builder
     */
    public static AlarmBuilder alarm() {
        return CeilometerAlarm.builder();
    }

    /**
     * The builder which creates a BlockQuotaSet
     *
     * @return the block quota-set builder
     */
    public static BlockQuotaSetBuilder blockQuotaSet() {
        return CinderBlockQuotaSet.builder();
    }

    /**
     * The builder which creates a sahara Data Source
     *
     * @return the data source builder
     */
    public static DataSourceBuilder dataSource() {
        return SaharaDataSource.builder();
    }

    /**
     * The builder which creates a sahara Job Binary
     *
     * @return the job binary builder
     */
    public static JobBinaryBuilder jobBinary() {
        return SaharaJobBinary.builder();
    }

    /**
     * The builder which creates a sahara Job
     *
     * @return the job builder
     */
    public static JobBuilder job() {
        return SaharaJob.builder();
    }

    /**
     * The builder which creates a job configuration for sahara job execution
     *
     * @return the job config builder
     */
    public static JobConfigBuilder jobConfig() {
        return SaharaJobConfig.builder();
    }

    /**
     * The builder which creates a sahara job execution
     *
     * @return the job execution builder
     */
    public static JobExecutionBuilder jobExecution() {
        return SaharaJobExecution.builder();
    }

    /**
     * The builder which creates manila security services
     *
     * @return the security service builder
     */
    public static SecurityServiceCreateBuilder securityService() {
        return ManilaSecurityServiceCreate.builder();
    }

    /**
     * The builder which creates manila share networks.
     *
     * @return the share network builder
     */
    public static ShareNetworkCreateBuilder shareNetwork() {
        return ManilaShareNetworkCreate.builder();
    }

    /**
     * The builder which creates manila shares.
     *
     * @return the share builder
     */
    public static ShareCreateBuilder share() {
        return ManilaShareCreate.builder();
    }

    /**
     * The builder which creates share types.
     *
     * @return the shae type builder
     */
    public static ShareTypeCreateBuilder shareType() {
        return ManilaShareTypeCreate.builder();
    }

    /**
     * The builder which creates manila share snapshots.
     *
     * @return the share builder
     */
    public static ShareSnapshotCreateBuilder shareSnapshot() {
        return ManilaShareSnapshotCreate.builder();
    }

    /**
     * The builder which creates manila share manages
     *
     * @return the share manage builder
     */
    public static ShareManageBuilder shareManage() {
        return ManilaShareManage.builder();
    }

    /**
     * The builder to create a Region
     *
     * @return the region builder
     */
    public static RegionBuilder region() {
        return KeystoneRegion.builder();
    }

    /**
     * The builder to create a Credential.
     *
     * @return the credential builder
     */
    public static CredentialBuilder credential() {
        return KeystoneCredential.builder();
    }

    /**
     * The builder to create a Domain.
     *
     * @return the domain builder
     */
    public static DomainBuilder domain() {
        return KeystoneDomain.builder();
    }

    /**
     * The builder to create a Endpoint.
     *
     * @return the endpoint builder
     */
    public static EndpointBuilder endpoint() {
        return KeystoneEndpoint.builder();
    }

    /**
     * The builder to create a Group.
     *
     * @return the group builder
     */
    public static GroupBuilder group() {
        return KeystoneGroup.builder();
    }

    /**
     * The builder to create a Policy.
     *
     * @return the policy builder
     */
    public static PolicyBuilder policy() {
        return KeystonePolicy.builder();
    }

    /**
     * The builder to create a Project.
     *
     * @return the project builder
     */
    public static ProjectBuilder project() {
        return KeystoneProject.builder();
    }

    /**
     * The builder to create a Role.
     *
     * @return the role builder
     */
    public static RoleBuilder role() {
        return KeystoneRole.builder();
    }

    /**
     * The builder to create a Service.
     *
     * @return the service builder
     */
    public static ServiceBuilder service() {
        return KeystoneService.builder();
    }

    /**
     * The builder to create a User.
     *
     * @return the user builder
     */
    public static UserBuilder user() {
        return KeystoneUser.builder();
    }

    /**
     * The builder which creates external policy for gbp
     *
     * @return the external policy builder
     */
    public static ExternalPolicyBuilder externalPolicy() {
        return GbpExternalPolicyCreate.builder();
    }

    /**
     * The builder which creates external segment for gbp
     *
     * @return the external segment builder
     */
    public static ExternalSegmentBuilder externalSegment() {
        return GbpExternalSegment.builder();
    }

    /**
     * The builder which creates L2 policy for gbp
     *
     * @return the L2 policy builder
     */
    public static L2PolicyBuilder l2Policy() {
        return GbpL2Policy.builder();
    }

    /**
     * The builder which creates L3 policy for gbp
     *
     * @return the L3 policy builder
     */
    public static L3PolicyBuilder l3Policy() {
        return GbpL3Policy.builder();
    }

    /**
     * The builder which creates nat pool for gbp
     *
     * @return the nat pool builder
     */
    public static NatPoolBuilder natPool() {
        return GbpNatPool.builder();
    }

    /**
     * The builder which creates network service policy for gbp
     *
     *
     * @return
     */
    public static NetworkServicePolicyBuilder networkServicePolicy() {
        return GbpNetworkServicePolicy.builder();
    }

    /**
     * The builder which creates policy action for gbp
     *
     * @return the policy action builder
     */
    public static PolicyActionCreateBuilder policyAction() {
        return GbpPolicyAction.builder();
    }

    /**
     * The builder which updates policy action for gbp
     *
     * @return the policy action builder
     */
    public static PolicyActionUpdateBuilder policyActionUpdate() {
        return GbpPolicyActionUpdate.builder();
    }

    /**
     * The builder which creates policy classifier for gbp
     *
     * @return the policy classifier builder
     */
    public static PolicyClassifierBuilder policyClassifier() {
        return GbpPolicyClassifier.builder();
    }

    /**
     * The builder which updates policy classifier for gbp
     *
     * @return the policy classifier builder
     */
    public static PolicyClassifierUpdateBuilder policyClassifierUpdate() {
        return GbpPolicyClassifierUpdate.builder();
    }

    /**
     * The builder which creates policy rule for gbp
     *
     * @return the policy rule builder
     */
    public static PolicyRuleBuilder policyRule() {
        return GbpPolicyRule.builder();
    }

    /**
     * The builder which creates policy rule set for gbp
     *
     * @return the policy rule set builder
     */
    public static PolicyRuleSetBuilder policyRuleSet() {
        return GbpPolicyRuleSet.builder();
    }

    /**
     * The builder which creates policy target for gbp
     *
     * @return the policy target builder
     */
    public static PolicyTargetBuilder policyTarget() {
        return GbpPolicyTarget.builder();
    }

    /**
     * The builder which creates policy target group for gbp
     *
     * @return the policy target group builder
     */
    public static PolicyTargetGroupBuilder policyTargetGroup() {
        return GbpPolicyTargetGroupCreate.builder();
    }

    /**
     * The builder which creates external routes for gbp
     *
     * @return the external routes builder
     */
    public static ExternalRoutesBuilder externalRoutes() {
        return GbpExternalRoutes.builder();
    }

    // Builders.<service>().<object>() ..

    /**
     * Identity V2 builders
     *
     * @return the keystone v2 builders
     */
    public static IdentityV2Builders identityV2() {
        return new KeystoneV2Builders();
    }

    /**
     * The Identity V3 builders
     *
     * @return the keystone v3 builders
     */
    public static IdentityV3Builders identityV3() {
        return new KeystoneV3Builders();
    }

    /**
     * The Compute builders
     *
     * @return the nova builders
     */
    public static ComputeBuilders compute() {
        return new NovaBuilders();
    }

    /**
     * The Storage builders
     *
     * @return the cinder builders
     */
    public static StorageBuilders storage() {
        return new CinderBuilders();
    }

    /**
     * The Orchestration builders
     *
     * @return the heat builders
     */
    public static OrchestrationBuilders heat() {
        return new HeatBuilders();
    }

    /**
     * The Network builders
     *
     * @return the neutron builders
     */
    public static NetworkBuilders neutron() {
        return new NeutronBuilders();
    }

    /**
     * The Sahara builders
     *
     * @return the sahara builders
     */
    public static DataProcessingBuilders sahara() {
        return new SaharaBuilders();
    }

    /**
     * The Ceilometer builders
     *
     * @return the ceilometer builders
     */
    public static TelemetryBuilders ceilometer() {
        return new CeilometerBuilders();
    }

    /**
     * The Manila builders
     *
     * @return the manila builders
     */
    public static SharedFileSystemBuilders manila() {
        return new ManilaBuilders();
    }

    /**
     * LbaasV2 pool builder
     *
     * @return the lb pool v2 builder
     */
    public static LbPoolV2Builder lbpoolV2() {
        return NeutronLbPoolV2.builder();
    }

    /**
     * LbaasV2 pool update builder
     *
     *
     * @return the lb pool v2 update builder
     */
    public static LbPoolV2UpdateBuilder lbPoolV2Update() {
        return NeutronLbPoolV2Update.builder();
    }

    /**
     * LbaasV2 member builder
     *
     *
     * @return the member v2 builder
     */
    public static MemberV2Builder memberV2() {
        return NeutronMemberV2.builder();
    }

    /**
     * LbaasV2 member update builder
     *
     *
     * @return the member v2 update builder
     */
    public static MemberV2UpdateBuilder memberV2Update() {
        return NeutronMemberV2Update.builder();
    }

    /**
     * LbaasV2 listener builder
     *
     *
     * @return the listener builder
     */
    public static ListenerV2Builder listenerV2() {
        return NeutronListenerV2.builder();
    }

    /**
     * LbaasV2 listener update builder
     *
     *
     * @return the listener v2 update builder
     */
    public static ListenerV2UpdateBuilder listenerV2Update() {
        return NeutronListenerV2Update.builder();
    }

    /**
     * LbaasV2 health monitor builder
     *
     *
     * @return the health monitor v2 builder
     */
    public static HealthMonitorV2Builder healthmonitorV2() {
        return NeutronHealthMonitorV2.builder();
    }

    /**
     * LbaasV2 healthmonitor update builder
     *
     *
     * @return the health monitor v2 update builder
     */
    public static HealthMonitorV2UpdateBuilder healthMonitorV2Update() {
        return NeutronHealthMonitorV2Update.builder();
    }

    /**
     * LbaasV2 loadbalancer builder
     *
     *
     * @return the loadbalancer v2 builder
     */
    public static LoadBalancerV2Builder loadbalancerV2() {
        return NeutronLoadBalancerV2.builder();
    }

    /**
     * LbaasV2 loadbalancer update builder
     *
     *
     * @return the loadbalancer v2 update builder
     */
    public static LoadBalancerV2UpdateBuilder loadBalancerV2Update() {
        return NeutronLoadBalancerV2Update.builder();
    }

}
