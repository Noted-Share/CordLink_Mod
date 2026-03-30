package com.cordlink;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DllInjector {

    public interface K32 extends Library {
        K32 I = Native.load("kernel32", K32.class);

        Pointer OpenProcess(int access, boolean inherit, int pid);
        boolean CloseHandle(Pointer handle);
        Pointer GetModuleHandleA(String name);
        Pointer GetProcAddress(Pointer module, String proc);
        Pointer VirtualAllocEx(Pointer proc, Pointer addr, int size, int type, int protect);
        boolean WriteProcessMemory(Pointer proc, Pointer addr, byte[] buf, int size, IntByReference written);
        Pointer CreateRemoteThread(Pointer proc, Pointer attr, int stack, Pointer start, Pointer param, int flags, IntByReference tid);
        int WaitForSingleObject(Pointer handle, int ms);
        boolean VirtualFreeEx(Pointer proc, Pointer addr, int size, int type);

        // Shared memory
        Pointer CreateFileMappingA(Pointer hFile, Pointer secAttr, int protect, int maxHigh, int maxLow, String name);
        Pointer OpenFileMappingA(int access, boolean inherit, String name);
        Pointer MapViewOfFile(Pointer hMap, int access, int offHigh, int offLow, int bytes);
        boolean UnmapViewOfFile(Pointer addr);

        // Toolhelp32 for module enumeration
        Pointer CreateToolhelp32Snapshot(int flags, int pid);
        boolean Module32FirstW(Pointer snapshot, MODULEENTRY32W me);
        boolean Module32NextW(Pointer snapshot, MODULEENTRY32W me);
    }

    @Structure.FieldOrder({"dwSize","th32ModuleID","th32ProcessID","GlblcntUsage","ProccntUsage","modBaseAddr","modBaseSize","hModule","szModule","szExePath"})
    public static class MODULEENTRY32W extends Structure {
        public int dwSize;
        public int th32ModuleID;
        public int th32ProcessID;
        public int GlblcntUsage;
        public int ProccntUsage;
        public Pointer modBaseAddr;
        public int modBaseSize;
        public Pointer hModule;
        public char[] szModule = new char[256];
        public char[] szExePath = new char[260];

        public MODULEENTRY32W() {
            dwSize = size();
        }

        public String getModuleName() {
            return new String(szModule).trim().replace("\0", "");
        }
    }

    private static final int PROCESS_ALL_ACCESS = 0x1FFFFF;
    private static final int MEM_COMMIT = 0x1000;
    private static final int MEM_RESERVE = 0x2000;
    private static final int MEM_RELEASE = 0x8000;
    private static final int PAGE_READWRITE = 0x04;
    private static final int TH32CS_SNAPMODULE = 0x00000008;
    private static final int TH32CS_SNAPMODULE32 = 0x00000010;
    private static final long INVALID_HANDLE_VALUE = -1L;

    public static boolean inject(int pid, String dllPath) {
        var k = K32.I;

        Pointer hProcess = k.OpenProcess(PROCESS_ALL_ACCESS, false, pid);
        if (hProcess == null || hProcess == Pointer.NULL) return false;

        try {
            byte[] pathBytes = (dllPath + "\0").getBytes(StandardCharsets.US_ASCII);

            Pointer remoteMem = k.VirtualAllocEx(hProcess, null, pathBytes.length,
                    MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
            if (remoteMem == null || remoteMem == Pointer.NULL) return false;

            if (!k.WriteProcessMemory(hProcess, remoteMem, pathBytes, pathBytes.length, null)) {
                k.VirtualFreeEx(hProcess, remoteMem, 0, MEM_RELEASE);
                return false;
            }

            Pointer hKernel32 = k.GetModuleHandleA("kernel32.dll");
            Pointer loadLibAddr = k.GetProcAddress(hKernel32, "LoadLibraryA");

            Pointer hThread = k.CreateRemoteThread(hProcess, null, 0, loadLibAddr, remoteMem, 0, null);
            if (hThread == null || hThread == Pointer.NULL) {
                k.VirtualFreeEx(hProcess, remoteMem, 0, MEM_RELEASE);
                return false;
            }

            k.WaitForSingleObject(hThread, 10000);
            k.CloseHandle(hThread);
            k.VirtualFreeEx(hProcess, remoteMem, 0, MEM_RELEASE);
            return true;
        } finally {
            k.CloseHandle(hProcess);
        }
    }

    private static boolean hasModule(int pid, String moduleName) {
        var k = K32.I;
        Pointer snap = k.CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
        if (snap == null || Pointer.nativeValue(snap) == INVALID_HANDLE_VALUE) return false;

        try {
            MODULEENTRY32W me = new MODULEENTRY32W();
            if (!k.Module32FirstW(snap, me)) return false;

            do {
                if (me.getModuleName().toLowerCase().contains(moduleName.toLowerCase())) {
                    return true;
                }
                me.dwSize = me.size();
            } while (k.Module32NextW(snap, me));

            return false;
        } finally {
            k.CloseHandle(snap);
        }
    }

    public static List<Long> findDiscordPids(String variant) {
        var allDiscord = ProcessHandle.allProcesses()
                .filter(p -> p.info().command().orElse("").toLowerCase().endsWith(variant.toLowerCase()))
                .toList();

        // Try to find the process with discord_voice.node loaded
        var result = new ArrayList<Long>();
        for (var p : allDiscord) {
            if (hasModule((int) p.pid(), "discord_voice.node")) {
                result.add(p.pid());
            }
        }

        // Fallback: if module enumeration failed (permissions), return all Discord PIDs
        if (result.isEmpty() && !allDiscord.isEmpty()) {
            for (var p : allDiscord) {
                result.add(p.pid());
            }
        }
        return result;
    }
}
