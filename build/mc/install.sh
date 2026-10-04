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

if [ "${MINT_MC_VARIANT:-mc}" = "ec" ]; then
	echo "MINT_MC_VARIANT=ec: skipping upstream mc install (expecting ec-provided repo/binary/tests)"
	exit 0
fi

# The version is pinned in ../.sdk-refs rather than resolved from the newest
# GitHub release, so the client binary and the test sources the suite runs are a
# function of this repository and not of when the build ran.
. "${MINT_ROOT_DIR}/.sdk-refs"
if [ -z "${MC_VERSION:-}" ]; then
	echo "MC_VERSION must be set in ${MINT_ROOT_DIR}/.sdk-refs"
	exit 1
fi

test_run_dir="$MINT_RUN_CORE_DIR/mc"

# The client binary used to come from dl.minio.io, which answers 410 Gone since
# the project retired that download host; the tagged GitHub release that the
# version above is derived from carries the same build as an asset. Fetch it
# under the name its checksum file uses and verify it before installing, so a
# truncated or substituted download fails the build instead of shipping.
# The asset name carries the architecture, as does the Go toolchain download in
# preinstall.sh, so this image is built for amd64 only.
mc_asset="mc.${MC_VERSION}"
mc_url="https://github.com/minio/mc/releases/download/${MC_VERSION}/mc.linux-amd64.${MC_VERSION}"
$WGET --output-document="${test_run_dir}/${mc_asset}" "${mc_url}"
$WGET --output-document="${test_run_dir}/${mc_asset}.sha256sum" "${mc_url}.sha256sum"
(cd "${test_run_dir}" && sha256sum --check --strict "${mc_asset}.sha256sum")
mv "${test_run_dir}/${mc_asset}" "${test_run_dir}/mc"
rm -f "${test_run_dir}/${mc_asset}.sha256sum"
chmod a+x "${test_run_dir}/mc"

git clone --quiet https://github.com/minio/mc.git "$test_run_dir/mc.git"
(
	cd "$test_run_dir/mc.git"
	git checkout --quiet "tags/${MC_VERSION}"
)
cp -a "${test_run_dir}/mc.git/functional-tests.sh" "$test_run_dir/"
rm -fr "$test_run_dir/mc.git"
