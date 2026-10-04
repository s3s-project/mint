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

# The version is pinned in ../.sdk-refs rather than resolved from the newest
# GitHub release, so the test source this suite compiles is a function of this
# repository and not of when the build ran.
. "${MINT_ROOT_DIR}/.sdk-refs"
if [ -z "${MINIO_GO_VERSION:-}" ]; then
	echo "MINIO_GO_VERSION must be set in ${MINT_ROOT_DIR}/.sdk-refs"
	exit 1
fi

test_run_dir="$MINT_RUN_CORE_DIR/minio-go"
curl -sL -o "${test_run_dir}/main.go" "https://raw.githubusercontent.com/minio/minio-go/${MINIO_GO_VERSION}/functional_tests.go"

# Extract only the function from versioning_test.go (skip package, imports, comments)
# Start from line 34 where the function definition begins
tail -n +34 "${test_run_dir}/versioning_test.go" >>"${test_run_dir}/main.go"

# Patch functional_tests.go to call our versioning test
# Add testBucketVersioningExcludedPrefixes() call after testStatObjectWithVersioning()
sed -i.bak '/testStatObjectWithVersioning()/a\
		testBucketVersioningExcludedPrefixes()
' "${test_run_dir}/main.go"

# Build the combined file
(cd "$test_run_dir" && go mod tidy -compat=1.21 && CGO_ENABLED=0 go build --ldflags "-s -w" -o minio-go main.go)
