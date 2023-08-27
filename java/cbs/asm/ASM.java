package cbs.asm;

import cbs.plugin.EmulationManager;
import com.google.common.io.Resources;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ASM {
    private ASM() {}
    private static ClassLoader ASM = null;
    private static final String ASM_VERSION = "9.5";
    private static final String ASM_DOWNLOAD = "https://repo.maven.apache.org/maven2/org/ow2/asm/$1/" + ASM_VERSION + "/$1-" + ASM_VERSION + ".jar";

    public static ClassLoader get(File dir, String... with) {
        if (ASM == null) {
            try {
                Class.forName("org.objectweb.asm.Opcodes").getField("ASM9").getInt(null);
                ASM = EmulationManager.class.getClassLoader();
            } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchFieldException | NoSuchFieldError | IllegalAccessException x) {
                boolean announced = false;
                dir.mkdirs();
                File asm = new File(dir, "asm-" + ASM_VERSION + ".jar");
                Logger log = Bukkit.getLogger();
                if (!asm.exists()) {
                    announced = true;
                    log.info(">> Downloading ASM " + ASM_VERSION);
                    try (FileOutputStream fin = new FileOutputStream(asm)) {
                        Resources.copy(new URL(ASM_DOWNLOAD.replace("$1", "asm")), fin);
                    } catch (Throwable e) {
                        asm.delete();
                        e.printStackTrace();
                    }
                }
                if (asm.exists()) {
                    if (announced) log.info(">> ASM download complete");
                    try {
                        ASM = new LibraryClassLoader(new URL[]{EmulationManager.class.getProtectionDomain().getCodeSource().getLocation(), asm.toURI().toURL()}, with);
                    } catch (Throwable e) {
                        log.log(Level.SEVERE, ">> Could not load ASM:", e);
                    }
                } else {
                    log.log(Level.SEVERE, ">> Could not load ASM:", new FileNotFoundException());
                }
            }
        }
        return ASM;
    }
}
