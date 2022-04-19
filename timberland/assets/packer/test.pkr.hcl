packer {
  required_plugins {
    docker = {
      version = ">= 0.0.7"
      source = "github.com/hashicorp/docker"
    }
  }
}

source "qemu" "example" {
  iso_url           = "https://mirror.chpc.utah.edu/pub/vault.centos.org/6.9/isos/x86_64/CentOS-6.9-x86_64-minimal.iso"
  iso_checksum      = "md5:af4a1640c0c6f348c6c41f1ea9e192a2"
  headless = true
  output_directory  = "output_centos_tdhtest"
  shutdown_command  = "echo 'packer' | sudo -S shutdown -P now"
  disk_size         = "5000M"
  format            = "qcow2"
  accelerator       = "tcg"
  http_directory    = "."
  ssh_username      = "root"
  ssh_password      = "s0m3password"
  ssh_timeout       = "20m"
  vm_name           = "tdhtest"
  net_device        = "virtio-net"
  disk_interface    = "virtio"
  boot_wait         = "10s"
  boot_command      = ["<tab> text ks=http://{{ .HTTPIP }}:{{ .HTTPPort }}/centos6-ks.cfg<enter><wait>"]
}

build {
  sources = ["source.qemu.example"]
}
