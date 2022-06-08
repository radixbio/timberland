# Timberland
Timberland is one of Radix's main projects, and one of the more complex ones.

If you need help with it, reach out to `alex@radix.bio` or `dhash@radix.bio`

It encompasses several responsibilities, and isn't *really* a product we sell, but several of the claims we make are substantiated by Timberland

In prose, it encompasses our infrastructure layer and is responsible for delivering a developer experience to Radix's internal dev team that is capable of unifying deployment across multiple operating systems, complex customer networks (firewalls, NAT's), and healing when something goes wrong. To do this, it relys heavily on the Hashicorp stack, with the tools of `terraform`, `nomad`, `consul`, and `vault` being key cornerstones to implement Timberland.

In a list, timberland's responsibilities are:

* Bootstrap a cluster and configuration store
* Start up the Radix Backend
* Bootstrap ACL tokens
* Bootstrap SSL
* Provide a SDN layer to traverse corporate networks
* Elect leaders when nodes fail / come back online via raft
* Provide healthchecks
* Nominally address services (rather than IP:port) in order to not worry about DHCP
* When new nodes come online, fingerprint them to possibly roll out drivers if they are attached
* Provide underlying storage abstractions via IPFS
* Run our databases
* Update and version deployments
* Provide a centralized management plane
* Provide a module system to easily add new components without having to be Radix
* Interact uniformly with Firecracker VM's, QEMU/KVM VM's, Docker containers, Chroot binaries, and binaries running un-isolated

