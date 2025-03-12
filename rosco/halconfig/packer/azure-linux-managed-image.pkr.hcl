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

variable "azure_image_name" {
  type    = string
  default = ""
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

variable "azure_custom_managed_image_name" {
  type    = string
  default = ""
}

variable "azure_managed_image_name" {
  type    = string
  default = ""
}

variable "azure_resource_group" {
  type    = string
  default = ""
}

variable "azure_storage_account" {
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

source "azure-arm" "azure-linux-managed-image" {
  client_id       = "${var.azure_client_id}"
  client_secret   = "${var.azure_client_secret}"
  subscription_id = "${var.azure_subscription_id}"
  tenant_id       = "${var.azure_tenant_id}"

  custom_managed_image_name                = "${var.azure_custom_managed_image_name}"
  custom_managed_image_resource_group_name = "${var.azure_resource_group}"

  location                          = "${var.azure_location}"
  managed_image_name                = "${var.azure_managed_image_name}"
  managed_image_resource_group_name = "${var.azure_resource_group}"

  os_type = "Linux"
  vm_size = "Standard_DS2_v2"

  polling_duration_timeout = "1h5m2s"
}

build {
  sources = ["source.azure-arm.azure-linux-managed-image"]

  provisioner "shell" {
    environment_vars = [
      "repository=${var.repository}", "package_type=${var.package_type}", "packages=${var.packages}",
      "upgrade=${var.upgrade}"
    ]
    pause_before = "20s"
    script       = "${var.configDir}/install_packages.sh"
  }

  provisioner "shell" {
    execute_command = "chmod +x {{ .Path }}; {{ .Vars }} sudo -E sh '{{ .Path }}'"
    inline          = [
      "apt-get update", "apt-get upgrade -y", "/usr/sbin/waagent -force -deprovision+user && export HISTSIZE=0 && sync"
    ]
    inline_shebang = "/bin/sh -x"
  }

}
