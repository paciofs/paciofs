/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package main

import (
	"fmt"
	"github.com/paciofs/paciofs/paciofs-csi/driver"
)

func main() {
	fmt.Println("PacioFS CSI Driver.")
	servers := &driver.CSIDriverServers{}
	fmt.Println(servers)
}
