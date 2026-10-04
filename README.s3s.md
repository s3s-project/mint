# s3s branch — unofficial mint image builds

This branch is **not** upstream mint. It exists so that [s3s](https://github.com/s3s-project/s3s) can keep running the mint suite from an image it controls, built from the SDK revisions it verified.

## Why this branch exists

`minio/mint` has been archived, and its `edge` tag on Docker Hub is a moving target, so an image pulled today is not the image pulled tomorrow. What is inside it is not reproducible either: `build/minio-js/install.sh` checks out the newest tag of `minio/minio-js` and `build/minio-java/install.sh` installs the newest release from Maven, so two builds of the same commit install different test sources.

- This branch sits on top of `master`, which is kept identical to `minio/mint` `master`; everything s3s adds lives here only.
- Pinned SDK revisions: [`.sdk-refs`](./.sdk-refs).
- Fixes applied to the SDK checkouts: [`patches/README.md`](./patches/README.md).
- Build recipe: [`Dockerfile.s3s`](./Dockerfile.s3s). Upstream's [`Dockerfile`](./Dockerfile) is left untouched.
- Publish workflow: [`.github/workflows/publish-ghcr.yml`](./.github/workflows/publish-ghcr.yml) → `ghcr.io/s3s-project/mint`.

## What this build changes

- The SDK revisions are pinned, so the test sources in the image are a function of this repository instead of "whatever was newest when the build ran".
- The patches in [`patches/`](./patches) fix cases that fail because of mint's own test code, or because a client library disagrees with AWS. [`patches/README.md`](./patches/README.md) states that admission rule and answers it for every patch.
- The revisions are pinned to the ones the archived `minio/mint:edge` image carries, so a patched build can be compared with that image one suite at a time.

## Images

- `ghcr.io/s3s-project/mint:<yyyymmddhhmm>` — one tag per publish, named with the UTC minute of the tag that triggered it.
- Every image records the commit it was built from in the `org.opencontainers.image.revision` label, so the tag does not have to name the source.
- There is deliberately **no** `edge` tag here, unlike the MinIO image this fork sits next to: the point of this image is that a consumer pins a digest, and a tag that moves would undo exactly that.

Consumers should pin the digest rather than a tag.

## Building locally

```bash
docker build -f Dockerfile.s3s -t mint-s3s .
```

The build installs the toolchains, clones the SDK repositories at the revisions in [`.sdk-refs`](./.sdk-refs), applies [`patches/`](./patches) to them and compiles the suites. The `COPY` is split so that editing a patch or a test reuses the installed toolchain layer.

```bash
docker run --rm --network host \
    -e SERVER_ENDPOINT=localhost:9000 \
    -e ACCESS_KEY=minioadmin \
    -e SECRET_KEY=minioadmin \
    -v /tmp/mint-log:/mint/log \
    mint-s3s
```

Without arguments the entry point runs every suite; naming suites runs only those.

## Publishing

```bash
git tag -a "$(date -u +%Y%m%d%H%M)" -m "mint build"
git push origin s3s --follow-tags
```

A tag is only built when it points at this branch, which the workflow's guard job checks together with the tag shape. `workflow_dispatch` rebuilds the current commit for the current UTC minute, for example to pick up base image or SDK updates. The pushes are by digest and tagged afterwards, so the published timestamp tag always names one immutable manifest list.

## License and provenance

mint is licensed under the [Apache License 2.0](./LICENSE), and so are the SDK repositories whose test sources the image bundles. The patches applied to those sources are in [`patches/`](./patches), so the corresponding source of a published image is this repository at the commit recorded in the image's `org.opencontainers.image.revision` label.

This is an unofficial community build. It is **not affiliated with, sponsored by, or endorsed by MinIO, Inc.** "MinIO" is a trademark of its respective owner.
