variable "azure_client_id" {
  type      = string
  default   = ""
  sensitive = true
}

variable "azure_client_secret" {
  type      = string
  default   = ""
  sensitive = true
}

variable "azure_managed_image_name" {
  type    = string
  default = ""
}

variable "azure_custom_managed_image_name" {
  type    = string
  default = ""
}

variable "azure_location" {
  type    = string
  default = ""
}

variable "azure_object_id" {
  type    = string
  default = ""
}

variable "azure_resource_group" {
  type    = string
  default = ""
}

variable "azure_subscription_id" {
  type    = string
  default = ""
}

variable "azure_tenant_id" {
  type    = string
  default = ""
}

variable "build_host" {
  type    = string
  default = ""
}

variable "chocolateyVersion" {
  type    = string
  default = ""
}

variable "configDir" {
  type = string
}

variable "package_type" {
  type    = string
  default = ""
}

variable "packages" {
  type    = string
  default = ""
}

variable "repository" {
  type    = string
  default = ""
}

variable "upgrade" {
  type    = string
  default = ""
}

source "azure-arm" "azure-windows-managed-image" {
  client_id       = "${var.azure_client_id}"
  client_secret   = "${var.azure_client_secret}"
  tenant_id       = "${var.azure_tenant_id}"
  subscription_id = "${var.azure_subscription_id}"

  managed_image_name                = "${var.azure_managed_image_name}"
  managed_image_resource_group_name = "${var.azure_resource_group}"

  custom_managed_image_name                = "${var.azure_custom_managed_image_name}"
  custom_managed_image_resource_group_name = "${var.azure_resource_group}"
  os_type                                  = "Windows"

  location = "${var.azure_location}"
  vm_size  = "Standard_A2_v2"

  object_id = "${var.azure_object_id}"

  communicator   = "winrm"
  winrm_insecure = "true"
  winrm_timeout  = "5m"
  winrm_use_ssl  = "true"
  winrm_username = "packer"

  polling_duration_timeout = "1h5m2s"
}

build {
  sources = ["source.azure-arm.azure-windows-managed-image"]

  provisioner "powershell" {
    environment_vars = [
      "repository=${var.repository}",
      "package_type=${var.package_type}",
      "packages=${var.packages}",
      "upgrade=${var.upgrade}",
      "chocolateyVersion=${var.chocolateyVersion}"
    ]
    pause_before = "30s"
    scripts      = [
      "${var.configDir}/scripts/windows-configure-chocolatey.ps1",
      "${var.configDir}/scripts/windows-install-packages.ps1"
    ]
  }

  provisioner "powershell" {
    inline = ["& $env:WINDIR\\system32\\sysprep\\sysprep.exe /generalize /shutdown /oobe"]
  }

}
