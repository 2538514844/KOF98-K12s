package local.k98;

import java.io.File;

enum NeoMode {
    AES_EUR("家用机 · 欧洲", false),
    AES_JAP("家用机 · 日本（兼容）", false),
    MVS_EUR("街机 · 欧洲", true),
    MVS_USA("街机 · 美国", true),
    MVS_JAP("街机 · 日本", true);

    final String label;
    final boolean arcade;

    NeoMode(String label, boolean arcade) {
        this.label = label;
        this.arcade = arcade;
    }

    static NeoMode fromName(String name) {
        for (NeoMode mode : values()) {
            if (mode.name().equals(name)) return mode;
        }
        return AES_EUR;
    }

    File saveDirectory(File root) {
        File legacy = new File(root, "save");
        return this == AES_EUR ? legacy : new File(legacy, name());
    }

    File resumeFile(File root) {
        return new File(saveDirectory(root), "resume.bin");
    }

    boolean canResume(File root) {
        File file = resumeFile(root);
        return file.isFile() || new File(file.getPath() + ".bak").isFile();
    }
}
