#!/bin/bash


# Issue commands to gfsh to start locator and launch a server
echo "Starting locator and server..."
gfsh run --file=startCluster.gfsh
