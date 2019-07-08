#!/bin/bash

input=$1
output=$2

envsubst < "${input}" > "${output}"
