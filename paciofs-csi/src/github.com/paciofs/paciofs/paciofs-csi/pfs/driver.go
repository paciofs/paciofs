/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package pfs

import (
	"github.com/container-storage-interface/spec/lib/go/csi"
	"github.com/golang/glog"
)

type driver struct {
	endpoint string
	name     string
	nodeID   string
	version  string

	identity   csi.IdentityServer
	controller csi.ControllerServer
	node       csi.NodeServer
}

func NewDriver(endpoint string, name string, nodeID string, version string) *driver {
	glog.Infof("Driver: %v version: %v endpoint: %v node: %v", name, version, endpoint, nodeID)

	d := driver{
		endpoint: endpoint,
		name:     name,
		nodeID:   nodeID,
		version:  version,
	}

	return &d
}

func NewControllerServer(d *driver) *controllerServer {
	return &controllerServer{
		driver: d,
	}
}

func NewIdentityServer(d *driver) *identityServer {
	return &identityServer{
		driver: d,
	}
}

func NewNodeServer(d *driver) *nodeServer {
	return &nodeServer{
		driver: d,
	}
}

func (d *driver) Run() {
	d.controller = NewControllerServer(d)
	d.identity = NewIdentityServer(d)
	d.node = NewNodeServer(d)
}
