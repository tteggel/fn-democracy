#!/usr/bin/env bash
set -xeuo

pushd $(dirname $0)
SCRIPTPATH=$(pwd)

for j in {1..20}; do
     (for i in {1.10}; do
         curl -q -o /dev/null $1 > /dev/null 2>&1
     done)&
done

cleanup() {
    popd || true
}
trap cleanup EXIT
