#!/usr/bin/env python3
"""Safe, deterministic importer for an existing BlazeCircuitLab native source ZIP.

Dry-run by default. --apply writes missing files, never overwrites conflicts.
"""
import argparse
import pathlib
import re
import stat
import sys
import zipfile

MAX_FILES = 1000
MAX_BYTES = 128 * 1024 * 1024
ROOT = re.compile(r"^BlazeCircuitLab-native-v(\d+\.\d+\.\d+)/(.+)$")
REQUIRED = {
    'settings.gradle.kts', 'build.gradle.kts', 'app/build.gradle.kts',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/blaze/circuitlab/core/MnaSolver.kt',
}


def plan(archive, target):
    """Return validated (archive entry, destination, bytes) entries."""
    with zipfile.ZipFile(archive) as z:
        entries = [i for i in z.infolist() if not i.is_dir()]
        if not entries or len(entries) > MAX_FILES:
            raise ValueError('Empty or oversized archive entry count')
        if sum(i.file_size for i in entries) > MAX_BYTES:
            raise ValueError('Archive exceeds 128 MiB uncompressed limit')
        plans, seen, versions = [], set(), set()
        for info in entries:
            name = info.filename
            if name.startswith('/') or '\\' in name or '\x00' in name:
                raise ValueError(f'Unsafe path: {name!r}')
            if (info.external_attr >> 16) & 0o170000 == stat.S_IFLNK:
                raise ValueError(f'Symlink prohibited: {name}')
            m = ROOT.fullmatch(name)
            if not m:
                raise ValueError(f'Unexpected archive layout: {name}')
            version, rel = m.groups()
            versions.add(version)
            parts = pathlib.PurePosixPath(rel).parts
            if '..' in parts or '.' in parts or not parts:
                raise ValueError(f'Path traversal: {name}')
            if rel == 'README.md':
                rel = f'docs/handover/native-v{version}-README.md'
            if rel in seen:
                raise ValueError(f'Duplicate destination: {rel}')
            seen.add(rel)
            destination = target.joinpath(*pathlib.PurePosixPath(rel).parts)
            if any(p.is_symlink() for p in (destination, *destination.parents)):
                raise ValueError(f'Symlink in destination path: {destination}')
            data = z.read(info)
            if len(data) != info.file_size:
                raise ValueError(f'Corrupt archive entry: {name}')
            if destination.exists() and destination.read_bytes() != data:
                raise FileExistsError(f'Conflicting destination; refusing overwrite: {destination}')
            plans.append((rel, destination, data))
        if len(versions) != 1:
            raise ValueError(f'Mixed versions in one snapshot: {sorted(versions)}')
        missing = REQUIRED.difference(seen)
        if missing:
            raise ValueError(f'Snapshot missing Android source files: {sorted(missing)}')
        return plans, versions.pop()


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('archive', type=pathlib.Path)
    p.add_argument('--target', type=pathlib.Path, default=pathlib.Path('.'))
    p.add_argument('--apply', action='store_true', help='Write files (default: dry-run)')
    args = p.parse_args(argv)
    target = args.target.resolve()
    if not target.is_dir():
        p.error('--target must be an existing directory')
    files, version = plan(args.archive, target)
    print(f'BlazeCircuitLab native v{version}: {len(files)} files; '
          f'{sum(len(data) for _,_,data in files)} bytes; '
          f'mode={"APPLY" if args.apply else "DRY-RUN"}')
    if args.apply:
        for rel, path, data in files:
            path.parent.mkdir(parents=True, exist_ok=True)
            if not path.exists():
                path.write_bytes(data)
        print('Import complete. Review git status and run Gradle verification.')
    else:
        print('No files changed. Repeat with --apply after reviewing conflicts.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, FileExistsError, OSError, zipfile.BadZipFile) as e:
        print(f'ERROR: {e}', file=sys.stderr)
        sys.exit(2)
