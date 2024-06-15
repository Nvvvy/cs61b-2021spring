package gitlet;

import java.io.File;

import static gitlet.Repository.*;

public class MyUtils {
    static boolean safeDelObj(File file) {
        if (!isObjDir(file)) {
            throw new IllegalArgumentException("not .gitlet object directory");
        }
        if (!file.isDirectory()) {
            return file.delete();
        } else {
            return false;
        }
    }

    static boolean isObjDir(File f) {
        return OBJ_DIR.equals(f.getParentFile());
    }
}
