package com.cordlink;

import com.sun.jna.Pointer;

/**
 * Writes position data to a named shared memory region.
 * Layout (little-endian):
 *   [0-3]   uint32 sequence
 *   [4-7]   float  listenerX
 *   [8-11]  float  listenerY
 *   [12-15] float  listenerZ
 *   [16-19] float  listenerYaw
 *   [20-23] float  listenerPitch
 *   [24]    uint8  numSources (max 16)
 *   [25+]   per source (36 bytes each)
 */
public class SharedPositionWriter {
    private static final String SHM_NAME = "CordlinkPositionData";
    private static final int SHM_SIZE = 4096;
    private static final int SOURCE_ENTRY_SIZE = 45;
    private static final int SOURCES_OFFSET = 25;
    private static final int MAX_SOURCES = (2048 - SOURCES_OFFSET) / SOURCE_ENTRY_SIZE;
    private static final int REQUEST_OFFSET = 2048;
    private static final int REQUEST_NAME_SIZE = 17;
    private static final int FILE_MAP_WRITE = 0x0002;
    private static final int FILE_MAP_READ = 0x0004;
    private static final int PAGE_READWRITE = 0x04;
    private static final long INVALID_HANDLE = -1L;

    private Pointer hMap;
    private Pointer view;
    private int sequence = 0;

    public boolean open() {
        var k = DllInjector.K32.I;
        hMap = k.CreateFileMappingA(
                new Pointer(INVALID_HANDLE), null, PAGE_READWRITE, 0, SHM_SIZE, SHM_NAME);
        if (hMap == null || hMap == Pointer.NULL) return false;

        view = k.MapViewOfFile(hMap, FILE_MAP_WRITE | FILE_MAP_READ, 0, 0, SHM_SIZE);
        if (view == null || view == Pointer.NULL) {
            k.CloseHandle(hMap);
            hMap = null;
            return false;
        }
        return true;
    }

    /** Check if shared memory already exists (DLL already injected). */
    public boolean exists() {
        var k = DllInjector.K32.I;
        Pointer h = k.OpenFileMappingA(FILE_MAP_WRITE, false, SHM_NAME);
        if (h != null && h != Pointer.NULL) {
            k.CloseHandle(h);
            return true;
        }
        return false;
    }

    public void writeListener(float x, float y, float z, float yaw, float pitch) {
        if (view == null) return;
        view.setFloat(4, x);
        view.setFloat(8, y);
        view.setFloat(12, z);
        view.setFloat(16, yaw);
        view.setFloat(20, pitch);
    }

    /** Fast path: write only rotation (yaw/pitch) and commit. Used by high-frequency thread. */
    public void writeRotationAndCommit(float yaw, float pitch) {
        if (view == null) return;
        view.setFloat(16, yaw);
        view.setFloat(20, pitch);
        sequence++;
        view.setInt(0, sequence);
    }

    public void writeSources(String[] ids, float[] xs, float[] ys, float[] zs, int count) {
        if (view == null) return;
        int n = Math.min(count, MAX_SOURCES);
        view.setByte(SOURCES_OFFSET - 1, (byte) n);
        int offset = SOURCES_OFFSET;
        for (int i = 0; i < n; i++) {
            byte[] idBytes = ids[i].getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int idLen = Math.min(idBytes.length, 32);
            view.setByte(offset, (byte) idLen);
            for (int j = 0; j < 32; j++) {
                view.setByte(offset + 1 + j, j < idLen ? idBytes[j] : 0);
            }
            view.setFloat(offset + 33, xs[i]);
            view.setFloat(offset + 37, ys[i]);
            view.setFloat(offset + 41, zs[i]);
            offset += SOURCE_ENTRY_SIZE;
        }
    }

    public void commit() {
        if (view == null) return;
        sequence++;
        view.setInt(0, sequence);
    }

    /** Read requested player names from DLL (DLL -> mod section of SHM). */
    public java.util.Set<String> readRequestedNames() {
        var names = new java.util.HashSet<String>();
        if (view == null) return names;
        int count = view.getByte(REQUEST_OFFSET) & 0xFF;
        int offset = REQUEST_OFFSET + 1;
        for (int i = 0; i < count; i++) {
            int nameLen = view.getByte(offset) & 0xFF;
            if (nameLen > 16) nameLen = 16;
            byte[] buf = new byte[nameLen];
            for (int j = 0; j < nameLen; j++) {
                buf[j] = view.getByte(offset + 1 + j);
            }
            names.add(new String(buf, java.nio.charset.StandardCharsets.US_ASCII));
            offset += REQUEST_NAME_SIZE;
        }
        return names;
    }

    public void close() {
        var k = DllInjector.K32.I;
        if (view != null) { k.UnmapViewOfFile(view); view = null; }
        if (hMap != null) { k.CloseHandle(hMap); hMap = null; }
    }
}
