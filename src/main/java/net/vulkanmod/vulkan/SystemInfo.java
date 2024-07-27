package net.vulkanmod.vulkan;

import oshi.hardware.CentralProcessor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class SystemInfo {
    public static final String cpuInfo;

    static {
        cpuInfo = isRunningOnAndroid() ? getProcessorNameForAndroid() + (!getProcessorNameForAndroid().equals("Unknown CPU or SoC") && isCPUInfoAvailable() ? " (SoC)" : "") : getProcessorNameForDesktop();
    }

    public static String getProcessorNameForAndroid() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            return br.lines()
                    .filter(line -> line.startsWith("Hardware") || line.startsWith("processor") || line.startsWith("model name"))
                    .map(line -> line.split(":\\s+", 2)[1])
                    .findFirst()
                    .orElse("Unknown CPU or SoC");
        } catch (IOException e) {
            return "Unknown CPU or SoC";
        }
    }

    public static String getProcessorNameForDesktop() {
        try {
            oshi.SystemInfo systemInfo = new oshi.SystemInfo();
            CentralProcessor centralProcessor = systemInfo.getHardware().getProcessor();
            return String.format("%s", centralProcessor.getProcessorIdentifier().getName()).replaceAll("\\s+", " ");
        } catch (Exception e) {
            return getProcessorNameForAndroid();
        }
    }

    private static boolean isRunningOnAndroid() {
        String osName = System.getProperty("os.name").toLowerCase();
        return (osName.contains("linux") || osName.contains("android")) &&
                (System.getenv("POJAV_ENVIRON") != null ||
                System.getenv("SCL_ENVIRON") != null ||
                System.getenv("SCL_RENDERER") != null ||
                System.getenv("POJAV_RENDERER") != null);
    }

    private static boolean isCPUInfoAvailable() {
        File cpuInfoFile = new File("/proc/cpuinfo");
        return cpuInfoFile.exists() && cpuInfoFile.canRead();
    }
}
