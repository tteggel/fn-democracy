#!/usr/bin/env bash
set -xeuo pipefail

pushd $(dirname $0)
SCRIPTPATH=$(pwd)

for j in {1..20}; do
    (for i in {1.10}; do
         curl $1
     done)&
done

cleanup() {
    popd || true
}
trap cleanup EXIT
