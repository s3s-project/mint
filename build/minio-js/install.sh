#!/bin/bash -e
#
#  Mint (C) 2017 Minio, Inc.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# Checkout at /mint/test-run/minio-js/
# During run of the test copy it to the the /min/run/core/minio-js/minio-js

# The revision and the patch replace upstream's "newest tag" lookup; see
# ../.sdk-refs for the pin and ../patches/README.md for why the patch is here.
. "${MINT_ROOT_DIR}/.sdk-refs"

install_path="./test-run/minio-js/"
rm -rf $install_path

git clone https://github.com/minio/minio-js.git $install_path

cd $install_path || exit 0

echo "Using minio-js REVISION $MINIO_JS_REF"

git checkout "$MINIO_JS_REF" --force &>/dev/null

head="$(git rev-parse HEAD)"
if [ "$head" != "$MINIO_JS_REF" ]; then
	echo "checked out $head, expected $MINIO_JS_REF"
	exit 1
fi

git apply "${MINT_ROOT_DIR}/patches/0001-minio-js-stat-object-last-modified.patch"

npm install --quiet &>/dev/null
