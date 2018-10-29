/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package main

import (
	"flag"
	"fmt"
	"os"

	"github.com/spf13/cobra"

	"github.com/paciofs/paciofs/paciofs-csi/pfs"
)

var (
	endpoint string
	nodeID   string
)

func init() {
	// for glog
	flag.Set("logtostderr", "true")
}

func main() {
	flag.CommandLine.Parse([]string{})

	cmd := &cobra.Command{
		Use:   "PFS",
		Short: "CSI based PacioFS driver",
		Run: func(cmd *cobra.Command, args []string) {
			handle()
		},
	}

	cmd.Flags().AddGoFlagSet(flag.CommandLine)

	cmd.PersistentFlags().StringVar(&nodeID, "nodeid", "", "Node ID")
	cmd.MarkPersistentFlagRequired("nodeid")

	cmd.PersistentFlags().StringVar(&endpoint, "endpoint", "unix:///var/lib/kubelet/plugins/paciofs-csi/csi.sock",
		"CSI endpoint")
	cmd.MarkPersistentFlagRequired("endpoint")

	cmd.ParseFlags(os.Args[1:])
	if err := cmd.Execute(); err != nil {
		fmt.Fprintf(os.Stderr, "%s", err.Error())
		os.Exit(1)
	}

	os.Exit(0)
}

func handle() {
	d := pfs.NewDriver(nodeID, endpoint)
	d.Run()
}
