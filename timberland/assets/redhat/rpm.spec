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
/opt/radix/timberland/consul/config/consul.json
/opt/radix/timberland/consul/default-policy.hcl
/opt/radix/timberland/consul/consul.env.conf
/opt/radix/timberland/consul/consul
/opt/radix/timberland/git-branch-workspace-status.txt
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
/etc/systemd/system/vault.service
/opt/radix/timberland/vault/vault
/opt/radix/timberland/vault/vault.env.conf
/opt/radix/timberland/vault/read-consul-ui-policy.hcl
/opt/radix/timberland/vault/read-flag-config-policy.hcl
/opt/radix/timberland/vault/remote-access-policy.hcl
/opt/radix/timberland/vault/nomad-server-policy.hcl
/opt/radix/timberland/vault/nomad-cluster-role.json
/opt/radix/timberland/vault/vault_config.conf
/opt/radix/timberland/vault/vault-plugin-secrets-oauthapp
/opt/radix/timberland/terraform/terraform
/opt/radix/timberland/terraform/main
/opt/radix/timberland/terraform/main/main.tf
/opt/radix/timberland/terraform/main/variables.tf
/opt/radix/timberland/terraform/plugins/terraform-provider-nomad_v1.4.7_x4
/opt/radix/timberland/terraform/plugins/terraform-provider-consul_v2.7.0_x4
/opt/radix/timberland/terraform/plugins/terraform-provider-vault_v2.11.0_x4
/opt/radix/timberland/terraform/apprise
/opt/radix/timberland/terraform/apprise/apprise.tmpl
/opt/radix/timberland/terraform/apprise/main.tf
/opt/radix/timberland/terraform/apprise/outputs.tf
/opt/radix/timberland/terraform/apprise/variables.tf
/opt/radix/timberland/terraform/runtime
/opt/radix/timberland/terraform/runtime/runtime.tmpl
/opt/radix/timberland/terraform/runtime/main.tf
/opt/radix/timberland/terraform/runtime/outputs.tf
/opt/radix/timberland/terraform/runtime/variables.tf
/opt/radix/timberland/terraform/s3lts
/opt/radix/timberland/terraform/s3lts/s3lts.tmpl
/opt/radix/timberland/terraform/s3lts/main.tf
/opt/radix/timberland/terraform/s3lts/outputs.tf
/opt/radix/timberland/terraform/s3lts/variables.tf
/opt/radix/timberland/terraform/kafka
/opt/radix/timberland/terraform/kafka/kafka.tmpl
/opt/radix/timberland/terraform/kafka/main.tf
/opt/radix/timberland/terraform/kafka/outputs.tf
/opt/radix/timberland/terraform/kafka/variables.tf
/opt/radix/timberland/terraform/kafka_companions
/opt/radix/timberland/terraform/kafka_companions/kafka_companions.tmpl
/opt/radix/timberland/terraform/kafka_companions/main.tf
/opt/radix/timberland/terraform/kafka_companions/outputs.tf
/opt/radix/timberland/terraform/kafka_companions/variables.tf
/opt/radix/timberland/terraform/zookeeper
/opt/radix/timberland/terraform/zookeeper/zookeeper.tmpl
/opt/radix/timberland/terraform/zookeeper/main.tf
/opt/radix/timberland/terraform/zookeeper/outputs.tf
/opt/radix/timberland/terraform/zookeeper/variables.tf
/opt/radix/timberland/terraform/retool
/opt/radix/timberland/terraform/retool/retool.tmpl
/opt/radix/timberland/terraform/retool/main.tf
/opt/radix/timberland/terraform/retool/outputs.tf
/opt/radix/timberland/terraform/retool/variables.tf
/opt/radix/timberland/terraform/elemental
/opt/radix/timberland/terraform/elemental/elemental.tmpl
/opt/radix/timberland/terraform/elemental/main.tf
/opt/radix/timberland/terraform/elemental/outputs.tf
/opt/radix/timberland/terraform/elemental/variables.tf
/opt/radix/timberland/terraform/elasticsearch
/opt/radix/timberland/terraform/elasticsearch/elastic_search.tmpl
/opt/radix/timberland/terraform/elasticsearch/main.tf
/opt/radix/timberland/terraform/elasticsearch/outputs.tf
/opt/radix/timberland/terraform/elasticsearch/variables.tf
/opt/radix/timberland/terraform/minio
/opt/radix/timberland/terraform/minio/minio.tmpl
/opt/radix/timberland/terraform/minio/main.tf
/opt/radix/timberland/terraform/minio/outputs.tf
/opt/radix/timberland/terraform/minio/variables.tf
/opt/radix/timberland/terraform/yugabyte
/opt/radix/timberland/terraform/yugabyte/yugabyte.tmpl
/opt/radix/timberland/terraform/yugabyte/main.tf
/opt/radix/timberland/terraform/yugabyte/outputs.tf
/opt/radix/timberland/terraform/yugabyte/variables.tf
/opt/radix/timberland/terraform/es_kafka_connector/es_kafka_connector.tmpl
/opt/radix/timberland/terraform/es_kafka_connector/main.tf
/opt/radix/timberland/terraform/es_kafka_connector/outputs.tf
/opt/radix/timberland/terraform/es_kafka_connector/variables.tf
/opt/radix/timberland/terraform/retool_pg_kafka_connector/retool_pg_kafka_connector.tmpl
/opt/radix/timberland/terraform/retool_pg_kafka_connector/main.tf
/opt/radix/timberland/terraform/retool_pg_kafka_connector/outputs.tf
/opt/radix/timberland/terraform/retool_pg_kafka_connector/variables.tf
/opt/radix/timberland/terraform/yugabyte_kafka_connector/yugabyte_kafka_connector.tmpl
/opt/radix/timberland/terraform/yugabyte_kafka_connector/main.tf
/opt/radix/timberland/terraform/yugabyte_kafka_connector/outputs.tf
/opt/radix/timberland/terraform/yugabyte_kafka_connector/variables.tf

%post
if [ -f /opt/radix/timberland/exec/timberland ]; then
    cd /opt/radix/timberland/exec/
    ./timberland runtime dns up
fi
mkdir -p /opt/radix/terraform
mkdir -p /opt/radix/zookeeper_data
mkdir -p /opt/radix/kafka_data
mkdir -p /opt/radix/ybmaster_data
mkdir -p /opt/radix/ybtserver_data

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
