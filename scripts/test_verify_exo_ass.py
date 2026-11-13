#!/usr/bin/env python3
"""Exercise release checks against the shipped ARM libraries and deliberately broken APKs."""
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

from verify_exo_ass import ABI_ELF, NATIVE, digest, verify_apk, verify_elf


class AssPublicationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = {abi: (NATIVE / "prebuilt" / abi / "libexo_ass.so").read_bytes() for abi in ABI_ELF}
        cls.native = {abi: {"sha256": digest(data)} for abi, data in cls.data.items()}

    def apk(self, entries, compression=zipfile.ZIP_DEFLATED):
        directory = tempfile.TemporaryDirectory(prefix="exo-ass-apk-")
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "test.apk"
        with zipfile.ZipFile(path, "w", compression=compression) as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return path

    def test_real_elf32_and_elf64(self):
        for abi, data in self.data.items():
            with self.subTest(abi=abi):
                result = verify_elf(data, abi)
                self.assertEqual(ABI_ELF[abi][1], result["machine"])
                self.assertEqual(24, result["api"])
                self.assertTrue(result["load_alignment"])

    def test_swapped_abi_rejected(self):
        for abi, other in [("arm64-v8a", "armeabi-v7a"), ("armeabi-v7a", "arm64-v8a")]:
            with self.subTest(abi=abi), self.assertRaisesRegex(ValueError, "architecture mismatch"):
                verify_elf(self.data[other], abi)

    def test_truncated_program_headers_rejected(self):
        for abi, data in self.data.items():
            with self.subTest(abi=abi), self.assertRaisesRegex(ValueError, "program headers"):
                verify_elf(data[:64], abi)

    def test_load_alignment_and_writable_code_rejected(self):
        for abi, data in self.data.items():
            is64 = data[4] == 2
            phoff = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
            phsize, phnum = struct.unpack_from("<HH", data, 54 if is64 else 42)
            load = next(phoff + i * phsize for i in range(phnum)
                        if struct.unpack_from("<I", data, phoff + i * phsize)[0] == 1)
            broken = bytearray(data)
            struct.pack_into("<Q" if is64 else "<I", broken, load + (48 if is64 else 28), 4096)
            with self.subTest(abi=abi, fault="alignment"), self.assertRaisesRegex(ValueError, "LOAD alignment"):
                verify_elf(broken, abi)
            broken = bytearray(data)
            struct.pack_into("<I", broken, load + (4 if is64 else 24), 7)
            with self.subTest(abi=abi, fault="writable code"), self.assertRaisesRegex(ValueError, "Writable executable"):
                verify_elf(broken, abi)

    def test_android_api_mismatch_rejected(self):
        for abi, data in self.data.items():
            with self.subTest(abi=abi), self.assertRaisesRegex(ValueError, "API 23 Android note"):
                verify_elf(data, abi, api=23)

    def test_regular_single_and_multi_abi_apks(self):
        for abis in [(abi,) for abi in self.data] + [tuple(self.data)]:
            with self.subTest(abis=abis):
                entries = {"lib/" + abi + "/libexo_ass.so": self.data[abi] for abi in abis}
                result = verify_apk(self.apk(entries), self.native)
                self.assertEqual(set(abis), set(result["libraries"]))

    def test_missing_ass_rejected(self):
        for abi in self.data:
            with self.subTest(abi=abi), self.assertRaisesRegex(ValueError, "missing ASS"):
                verify_apk(self.apk({"lib/" + abi + "/libplayer.so": b"other native library"}), self.native)

    def test_mixed_abi_package_rejected(self):
        entries = {"lib/arm64-v8a/libplayer.so": b"other native library",
                   "lib/armeabi-v7a/libexo_ass.so": self.data["armeabi-v7a"]}
        with self.assertRaisesRegex(ValueError, "mixes ABI"):
            verify_apk(self.apk(entries), self.native)

    def test_stale_or_wrong_library_rejected(self):
        for abi, other in [("arm64-v8a", "armeabi-v7a"), ("armeabi-v7a", "arm64-v8a")]:
            for payload in [self.data[abi] + b"stale", self.data[other]]:
                with self.subTest(abi=abi), self.assertRaisesRegex(ValueError, "stale or wrong-ABI"):
                    verify_apk(self.apk({"lib/" + abi + "/libexo_ass.so": payload}), self.native)

    def test_uncompressed_zip_alignment(self):
        for abi, data in self.data.items():
            name = "lib/" + abi + "/libexo_ass.so"
            with self.subTest(abi=abi, aligned=False), self.assertRaisesRegex(ValueError, "ZIP 16 KiB aligned"):
                verify_apk(self.apk({name: data}, zipfile.ZIP_STORED), self.native)
            info = zipfile.ZipInfo(name)
            padding = -(30 + len(name.encode())) % 16384
            info.extra = struct.pack("<HH", 0xFFFF, padding - 4) + bytes(padding - 4)
            with self.subTest(abi=abi, aligned=True):
                result = verify_apk(self.apk({info: data}, zipfile.ZIP_STORED), self.native)
                self.assertEqual(0, result["libraries"][abi]["data_offset"] % 16384)


if __name__ == "__main__":
    unittest.main()
