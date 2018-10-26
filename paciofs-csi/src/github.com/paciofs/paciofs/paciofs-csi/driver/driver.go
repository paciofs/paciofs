/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package driver

import (
	"github.com/container-storage-interface/spec/lib/go/csi/v0"
)

type CSIDriverServers struct {
	Controller csi.ControllerServer
	Identity   csi.IdentityServer
	Node       csi.NodeServer
}
