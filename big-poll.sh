#!/usr/bin/env bash
set -xeuo pipefail

pushd $(dirname $0)
SCRIPTPATH=$(pwd)

for j in {1..11}; do
    (
     for i in {1..10}; do
         curl -q -o /dev/null $1 > /dev/null 2>&1 &
     done
     wait
    )
done

cleanup() {
    popd || true
}
trap cleanup EXIT
