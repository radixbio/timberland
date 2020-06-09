Summary: Radix Labs Runtime launcher!
Name: radix-timberland
Version: 0
Release: 0
License: BSD
URL: http://radix.bio
Requires: java-11-openjdk
Requires: docker-ce

# Do not try to use magic to determine file types
%define __spec_install_pre %{nil}
%define __spec_install_post %{nil}

# Do not die because we give it more input files than are in the files section.
# This is needed because of where the pkg_rpm Bazel rule places the data files.
%define _unpackaged_files_terminate_build 0

%description
Timberland is a launcher designed to run backing
services for the Radix Runtime

%prep
tar -xvf {timberland-full-tar.tar}
rm {timberland-full-tar.tar}

%files
/opt/radix/timberland/consul/consul.json
/opt/radix/timberland/consul/consul.env.conf
/opt/radix/timberland/consul/consul
/opt/radix/timberland/exec/timberland
/opt/radix/timberland/exec/timberland-bin_deploy.jar
/opt/radix/timberland/exec/timberland-launcher_deploy.jar
/etc/networkd-dispatcher/routable.d/10-radix-consul
/etc/networkd-dispatcher/routable.d/10-radix-nomad
/opt/radix/timberland/nomad/nomad.env.conf
/opt/radix/timberland/nomad/nomad
/opt/radix/timberland/nomad/config/nomad.hcl
/opt/radix/timberland/nomad/config/elasticsearch/unicast_hosts.tpl
/opt/radix/timberland/nomad/zookeeper/zoo.tpl
/opt/radix/timberland/nomad/zookeeper/zoo.cfg
/opt/radix/timberland/nomad/zookeeper/zoo_replicated.cfg.dynamic
/etc/systemd/system/consul.service
/etc/systemd/system/nomad.service
/opt/radix/timberland/terraform/terraform
/opt/radix/timberland/terraform/main
/opt/radix/timberland/terraform/main/templates
/opt/radix/timberland/terraform/main/templates/retool_pg_kafka_connector.tmpl
/opt/radix/timberland/terraform/main/templates/elastic_search.tmpl
/opt/radix/timberland/terraform/main/templates/apprise.tmpl
/opt/radix/timberland/terraform/main/templates/retool.tmpl
/opt/radix/timberland/terraform/main/templates/yugabyte_kafka_connector.tmpl
/opt/radix/timberland/terraform/main/templates/kafka_companions.tmpl
/opt/radix/timberland/terraform/main/templates/kafka.tmpl
/opt/radix/timberland/terraform/main/templates/yugabyte.tmpl
/opt/radix/timberland/terraform/main/templates/elemental.tmpl
/opt/radix/timberland/terraform/main/templates/vault.tmpl
/opt/radix/timberland/terraform/main/templates/zookeeper.tmpl
/opt/radix/timberland/terraform/main/templates/minio.tmpl
/opt/radix/timberland/terraform/main/templates/es_kafka_connector.tmpl
/opt/radix/timberland/terraform/main/main.tf
/opt/radix/timberland/terraform/main/variables.tf
/opt/radix/timberland/terraform/plugins/terraform-provider-nomad_v1.4.5_x4
/opt/radix/timberland/terraform/plugins/terraform-provider-consul_v2.7.0_x4

%post
if [ -f /opt/radix/timberland/exec/timberland ]; then
    cd /opt/radix/timberland/exec/
    ./timberland runtime dns up
fi
mkdir -p /opt/radix/terraform

# The following seems to be needed to allow Docker containers to access the Internet.
# Note: This is not automatically removed from /etc/sysctl.conf upon package removal.
echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf
sysctl sysctl net.ipv4.ip_forward=1

%preun
if [ -f /opt/radix/timberland/exec/timberland ]; then
    cd /opt/radix/timberland/exec/
    ./timberland runtime dns down
fi
rm -rf /opt/radix/terraform
