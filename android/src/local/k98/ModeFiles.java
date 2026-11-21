package local.k98;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class ModeFiles {
    private static final long EXPORT_HOME_CRC = 0xd27a71f1L;
    private static final long JAPAN_HOME_COMPAT_CRC = 0xc084fb50L;
    private static final long SOUND_CRC = 0x94416d67L;
    private static final long FONT_CRC = 0x354029fcL;

    // The v0.4 data pack includes the export AES firmware and three regional MVS
    // firmwares. For Japanese home mode, build a region-only variant from the
    // user's already imported AES firmware. Keep the original game and BIOS ZIPs intact.
    static File prepare(File root, NeoMode mode) throws IOException {
        mkdir(mode.saveDirectory(root));
        File common = new File(root, "system");
        if (mode != NeoMode.AES_JAP) return common;
        File system = new File(root, "modes/AES_JAP/system");
        File directory = new File(system, "fbneo/patched");
        mkdir(directory);
        File overlay = new File(directory, "neogeo.zip");
        if (validOverlay(overlay)) return system;

        File temporary = File.createTempFile("jp-", ".tmp", directory);
        try {
            try (ZipFile firmware = new ZipFile(new File(root, "roms/neogeo.zip"));
                 ZipFile support = new ZipFile(new File(common, "fbneo/patched/neogeo.zip"));
                 FileOutputStream file = new FileOutputStream(temporary);
                 ZipOutputStream zip = new ZipOutputStream(file)) {
                byte[] home = readChip(firmware, "aes-bios.bin", EXPORT_HOME_CRC);
                // Region is byte 0x401 on the 68000 bus (0x400 in this word-swapped dump).
                home[0x400] = 0;
                if (crc(home) != JAPAN_HOME_COMPAT_CRC) throw new IOException("日版家用模式初始化失败");
                writeChip(zip, "neo-po.bin", home);
                writeChip(zip, "sm1.sm1", readChip(support, "sm1.sm1", SOUND_CRC));
                writeChip(zip, "sfix.sfix", readChip(support, "sfix.sfix", FONT_CRC));
                zip.finish();
                file.getFD().sync();
            }
            Files.move(temporary.toPath(), overlay.toPath(), StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
        return system;
    }

    private static boolean validOverlay(File file) {
        if (!file.isFile()) return false;
        try (ZipFile zip = new ZipFile(file)) {
            readChip(zip, "neo-po.bin", JAPAN_HOME_COMPAT_CRC);
            readChip(zip, "sm1.sm1", SOUND_CRC);
            readChip(zip, "sfix.sfix", FONT_CRC);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void mkdir(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("无法创建模式目录，请检查手机剩余空间");
    }

    private static long crc(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    private static byte[] readChip(ZipFile zip, String name, long expected) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getCrc() != expected || entry.getSize() != 131072)
            throw new IOException("模式数据校验失败：" + name);
        byte[] bytes = new byte[131072];
        try (DataInputStream input = new DataInputStream(zip.getInputStream(entry))) {
            input.readFully(bytes);
        }
        if (crc(bytes) != expected) throw new IOException("模式数据损坏：" + name);
        return bytes;
    }

    private static void writeChip(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
