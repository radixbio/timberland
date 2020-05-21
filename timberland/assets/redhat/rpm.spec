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

# Do not die becuse we give it more input files than are in the files section.
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
/opt/radix/timberland/nginx/nginx-minios.conf
/opt/radix/timberland/nginx/nginx-minio-noupstream.conf
/opt/radix/timberland/nginx/nginx-retool.conf
/opt/radix/timberland/nomad/nomad.env.conf
/opt/radix/timberland/nomad/nomad
/opt/radix/timberland/nomad/config/nomad.hcl
/opt/radix/timberland/nomad/config/elasticsearch/unicast_hosts.tpl
/opt/radix/timberland/nomad/zookeeper/zoo.tpl
/opt/radix/timberland/nomad/connect/postgres_source.sh
/opt/radix/timberland/nomad/connect/start.sh
/opt/radix/timberland/nomad/connect/yugabyte_sink.sh
/etc/systemd/network/dummy0.netdev
/etc/systemd/network/dummy0.network
/etc/systemd/system/consul.service
/etc/systemd/system/nomad.service
/opt/radix/timberland/terraform/terraform
