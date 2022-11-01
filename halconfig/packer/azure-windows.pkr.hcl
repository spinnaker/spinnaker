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

variable "azure_image_offer" {
  type    = string
  default = ""
}

variable "azure_image_publisher" {
  type    = string
  default = ""
}

variable "azure_image_sku" {
  type    = string
  default = ""
}

variable "azure_location" {
  type    = string
  default = ""
}

variable "azure_managed_image_name" {
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

source "azure-arm" "azure-windows" {
  client_id       = "${var.azure_client_id}"
  client_secret   = "${var.azure_client_secret}"
  subscription_id = "${var.azure_subscription_id}"
  tenant_id       = "${var.azure_tenant_id}"
  object_id       = "${var.azure_object_id}"

  image_offer     = "${var.azure_image_offer}"
  image_publisher = "${var.azure_image_publisher}"
  image_sku       = "${var.azure_image_sku}"

  location                          = "${var.azure_location}"
  managed_image_name                = "${var.azure_managed_image_name}"
  managed_image_resource_group_name = "${var.azure_resource_group}"

  os_type = "Windows"
  vm_size = "Standard_A2_v2"

  communicator   = "winrm"
  winrm_insecure = "true"
  winrm_timeout  = "5m"
  winrm_use_ssl  = "true"
  winrm_username = "packer"
}

build {
  sources = ["source.azure-arm.azure-windows"]

  provisioner "powershell" {
    environment_vars = [
      "repository=${var.repository}", "package_type=${var.package_type}", "packages=${var.packages}",
      "upgrade=${var.upgrade}", "chocolateyVersion=${var.chocolateyVersion}"
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
