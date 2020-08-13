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
###AUTOFILLED###

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
