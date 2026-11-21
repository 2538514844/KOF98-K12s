package local.k98;
final class Bridge {
    static { System.loadLibrary("retro"); System.loadLibrary("k98front"); }
    // Keep the single-argument entry point used by the existing desktop test clients.
    static int open(String root) {
        java.io.File base = new java.io.File(root);
        return openMode(root, new java.io.File(base, "system").getPath(),
                        NeoMode.AES_EUR.saveDirectory(base).getPath(), NeoMode.AES_EUR.name());
    }
    static native int openMode(String root, String system, String save, String mode);
    static native int rate();
    static native double fps();
    static native int frame(int mask,int[] rgb,short[] pcm,int[] geometry);
    static native int stateSize();
    static native boolean state(byte[] bytes,boolean writing);
    static native void close();
}
