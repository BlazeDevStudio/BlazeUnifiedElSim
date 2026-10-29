import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

PATH = pathlib.Path(__file__).resolve().parents[1] / 'tools' / 'import_snapshot.py'
spec = importlib.util.spec_from_file_location('import_snapshot', PATH)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def create_zip(path, names):
    with zipfile.ZipFile(path, 'w') as z:
        for item in names:
            z.writestr(item, b'bytes')


class MigrationSafetyTests(unittest.TestCase):
    def test_unsafe_path(self):
        with tempfile.TemporaryDirectory() as d:
            z = pathlib.Path(d) / 'unsafe.zip'
            create_zip(z, ['BlazeCircuitLab-native-v0.2.9/../../evil'])
            with self.assertRaises(ValueError):
                m.plan(z, pathlib.Path(d))

    def test_missing_native_source(self):
        with tempfile.TemporaryDirectory() as d:
            z = pathlib.Path(d) / 'incomplete.zip'
            create_zip(z, ['BlazeCircuitLab-native-v0.2.9/README.md'])
            with self.assertRaisesRegex(ValueError, 'missing Android source'):
                m.plan(z, pathlib.Path(d))

    def test_mixed_versions(self):
        with tempfile.TemporaryDirectory() as d:
            z = pathlib.Path(d) / 'mixed.zip'
            paths = [f'BlazeCircuitLab-native-v0.2.9/{x}' for x in m.REQUIRED]
            paths += ['BlazeCircuitLab-native-v0.2.8/README.md']
            create_zip(z, paths)
            with self.assertRaisesRegex(ValueError, 'Mixed versions'):
                m.plan(z, pathlib.Path(d))

    def test_readme_handover_not_root_override(self):
        with tempfile.TemporaryDirectory() as d:
            z = pathlib.Path(d) / 'valid.zip'
            paths = [f'BlazeCircuitLab-native-v0.2.9/{x}' for x in m.REQUIRED]
            paths += ['BlazeCircuitLab-native-v0.2.9/README.md']
            create_zip(z, paths)
            files, version = m.plan(z, pathlib.Path(d))
            self.assertEqual(version, '0.2.9')
            self.assertIn('docs/handover/native-v0.2.9-README.md', [f[0] for f in files])
            self.assertNotIn('README.md', [f[0] for f in files])

    def test_existing_different_file_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            z = pathlib.Path(d) / 'valid.zip'
            paths = [f'BlazeCircuitLab-native-v0.2.9/{x}' for x in m.REQUIRED]
            create_zip(z, paths)
            f = pathlib.Path(d) / 'settings.gradle.kts'
            f.write_text('do not replace')
            with self.assertRaises(FileExistsError):
                m.plan(z, pathlib.Path(d))


if __name__ == '__main__':
    unittest.main()
