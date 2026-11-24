import hashlib
from http.client import IncompleteRead
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch
from zipfile import ZipFile

import build
from scripts import package_release, prepare_build


class DependenciesTest(unittest.TestCase):
    def test_interrupted_http_response_is_retried(self):
        response = MagicMock()
        response.__enter__.return_value.read.side_effect = [IncompleteRead(b"partial", 10), b"complete"]
        with patch.object(prepare_build, "urlopen", return_value=response) as open_url, \
                patch.object(prepare_build.time, "sleep"):
            self.assertEqual(prepare_build.fetch("https://example.test/dependency"), b"complete")
            self.assertEqual(open_url.call_count, 2)

    def test_download_checks_hash_and_repairs_bad_cache(self):
        data = b"verified dependency"
        hashes = {"sha256": hashlib.sha256(data).hexdigest()}
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "libs/dependency.jar"
            with patch.object(prepare_build, "fetch", return_value=data) as fetch:
                prepare_build.download("https://example.test/dependency", target, hashes)
                prepare_build.download("https://example.test/dependency", target, hashes)
                self.assertEqual(fetch.call_count, 1)
                target.write_bytes(b"broken cached file")
                prepare_build.download("https://example.test/dependency", target, hashes)
                self.assertEqual(fetch.call_count, 2)
            self.assertEqual(target.read_bytes(), data)

    def test_download_rejects_corruption_without_overwriting(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "dependency.jar"
            target.write_bytes(b"original")
            with patch.object(prepare_build, "fetch", return_value=b"wrong"):
                with self.assertRaisesRegex(ValueError, "mismatch"):
                    prepare_build.download("https://example.test/dependency", target, {"sha1": "0" * 40})
            self.assertEqual(target.read_bytes(), b"original")

    def test_checksum_is_required(self):
        with self.assertRaisesRegex(ValueError, "No checksum"):
            prepare_build.verify(b"data", {}, "dependency.jar")

    def test_maven_coordinate_paths(self):
        self.assertEqual(prepare_build.maven_path("net.fabricmc:fabric-loader:0.20.1"),
                         "net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar")
        self.assertEqual(prepare_build.maven_path("example:lib:1.0:natives-linux"),
                         "example/lib/1.0/lib-1.0-natives-linux.jar")


class PackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = package_release.release_version()
        self.mod = self.root / "mod.jar"
        self.api = self.root / "fabric-api.jar"
        self.write_jar(self.mod, "echo_lure", self.version)
        self.write_jar(self.api, "fabric-api", build.API_VERSION, {"LICENSE-fabric-api": b"Fabric API license"})

    def write_jar(self, target, mod_id, version, extra=None):
        with ZipFile(target, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({"id": mod_id, "version": version}))
            for name, data in (extra or {}).items():
                jar.writestr(name, data)

    def test_tag_must_match_mod_metadata(self):
        self.assertEqual(package_release.release_version("v" + self.version), self.version)
        self.assertEqual(package_release.release_version(self.version), self.version)
        with self.assertRaisesRegex(ValueError, "must match"):
            package_release.release_version("v999.0.0")

    def test_ready_to_copy_layout_and_checksums(self):
        output = package_release.package(self.mod, self.api, self.root / "release", self.version)
        mod_name = f"mods/echo-lure-{self.version}+mc{build.VERSION}.jar"
        api_name = f"mods/fabric-api-{build.API_VERSION}.jar"
        with ZipFile(output) as archive:
            self.assertEqual(set(archive.namelist()),
                             {mod_name, api_name, "INSTALL.txt", "SHA256SUMS.txt", "LICENSES/Fabric-API.txt"})
            self.assertEqual(archive.read(mod_name), self.mod.read_bytes())
            self.assertEqual(archive.read(api_name), self.api.read_bytes())
            self.assertIn("Не заменяй папку целиком", archive.read("INSTALL.txt").decode("utf-8"))
            for line in archive.read("SHA256SUMS.txt").decode("ascii").splitlines():
                digest, name = line.split("  ", 1)
                self.assertEqual(digest, hashlib.sha256(archive.read(name)).hexdigest())
        expected = f"{hashlib.sha256(output.read_bytes()).hexdigest()}  {output.name}\n"
        self.assertEqual(output.with_suffix(".zip.sha256").read_text(), expected)

    def test_zip_is_reproducible(self):
        first = package_release.package(self.mod, self.api, self.root / "a", self.version)
        second = package_release.package(self.mod, self.api, self.root / "b", self.version)
        self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_wrong_mod_version_is_not_packaged(self):
        self.write_jar(self.mod, "echo_lure", "0.0.0")
        with self.assertRaisesRegex(ValueError, "Unexpected mod/version"):
            package_release.package(self.mod, self.api, self.root / "release", self.version)
        self.assertFalse((self.root / "release").exists())

    def test_wrong_fabric_api_is_not_packaged(self):
        self.write_jar(self.api, "unrelated_mod", build.API_VERSION)
        with self.assertRaisesRegex(ValueError, "Unexpected mod/version"):
            package_release.package(self.mod, self.api, self.root / "release", self.version)


if __name__ == "__main__":
    unittest.main()
