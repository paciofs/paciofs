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
	identity   csi.IdentityServer
	controller csi.ControllerServer
	node       csi.NodeServer
}

const (
	driverName = "paciofs-csi"
	version    = "1.0.0"
)

func NewDriver(nodeID string, endpoint string) *driver {
	glog.Infof("Driver: %v version: %v", driverName, version)

	d := &driver{}

	return d
}

func (d *driver) Run() {
}
