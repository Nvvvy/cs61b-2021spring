package gitlet;

import java.io.File;

import static gitlet.Utils.*;
import static gitlet.Repository.*;

public class Blob {
    final String fileName;
    final String sha1;
    String content;

    public Blob(String name) {
        fileName = name;
        content = readContentsAsString(join(CWD, name));
        sha1 = sha1(content);
    }

    public String getSha1() {
        return sha1;
    }


    static String contentSha1(String name) {
        File target = join(CWD, name);
        return sha1(readContents(target));
    }

    static void saveBlob(Blob b) {
        File target = join(OBJ_DIR, b.sha1);
        writeContents(target, b.content);
    }

    public boolean isUpdated(String name) {
        String latestSha1 = contentSha1(name);
        return sha1.equals(latestSha1);
    }


}
