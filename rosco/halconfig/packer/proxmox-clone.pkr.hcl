packer {
  required_plugins {
    proxmox = {
      version = ">= 1.1.0"
      source  = "github.com/hashicorp/proxmox"
    }
  }
}

variable "proxmox_url" { type = string }
variable "proxmox_username" { type = string }
variable "proxmox_password" {
  type      = string
  default   = ""
  sensitive = true
}
variable "proxmox_node" { type = string }
variable "proxmox_clone_vm_id" { type = string }
variable "proxmox_vm_name" { type = string }
variable "proxmox_vm_id" {
  type    = string
  default = ""
}
variable "proxmox_storage" {
  type    = string
  default = "local-lvm"
}
variable "proxmox_cloud_init_storage" {
  type    = string
  default = "local"
}
variable "proxmox_ssh_username" {
  type    = string
  default = "ubuntu"
}
variable "proxmox_cores" {
  type    = string
  default = "1"
}
variable "proxmox_memory" {
  type    = string
  default = "512"
}
variable "proxmox_full_clone" {
  type    = string
  default = "true"
}
variable "proxmox_insecure_skip_tls_verify" {
  type    = string
  default = "false"
}
variable "appversion" {
  type    = string
  default = ""
}
variable "build_host" {
  type    = string
  default = ""
}
variable "build_info_url" {
  type    = string
  default = ""
}
variable "repository" {
  type    = string
  default = ""
}
variable "package_type" {
  type    = string
  default = "deb"
}
variable "packages" {
  type    = string
  default = ""
}
variable "upgrade" {
  type    = string
  default = ""
}
variable "configDir" { type = string }

source "proxmox-clone" "vm" {
  proxmox_url              = var.proxmox_url
  username                 = var.proxmox_username
  password                 = var.proxmox_password
  insecure_skip_tls_verify = var.proxmox_insecure_skip_tls_verify == "true"
  node                     = var.proxmox_node
  clone_vm_id              = tonumber(var.proxmox_clone_vm_id)
  vm_id                    = var.proxmox_vm_id == "" ? null : tonumber(var.proxmox_vm_id)
  vm_name                  = var.proxmox_vm_name
  full_clone               = var.proxmox_full_clone == "true"
  cores                    = tonumber(var.proxmox_cores)
  memory                   = tonumber(var.proxmox_memory)
  scsi_controller          = "virtio-scsi-pci"
  qemu_agent               = true
  cloud_init               = true
  cloud_init_storage_pool  = var.proxmox_cloud_init_storage

  communicator    = "ssh"
  ssh_username    = var.proxmox_ssh_username
  ssh_agent_auth  = true
  ssh_timeout     = "5m"

  template_name        = var.proxmox_vm_name
  template_description = "appversion: ${var.appversion}, build_host: ${var.build_host}, build_info_url: ${var.build_info_url}"
}

build {
  sources = ["source.proxmox-clone.vm"]

  provisioner "shell" {
    script = "${var.configDir}/install_packages.sh"
    environment_vars = [
      "repository=${var.repository}",
      "package_type=${var.package_type}",
      "packages=${var.packages}",
      "upgrade=${var.upgrade}",
    ]
  }
}
