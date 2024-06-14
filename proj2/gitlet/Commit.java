package gitlet;


import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Utils.*;
import static gitlet.Repository.OBJ_DIR;

/**
 * Represents a gitlet commit object.
 *
 * <p>
 * A commit will consist of a log message, timestamp, a mapping of file names to blob references,
 * a parent reference, and (for merges) a second parent reference.
 *
 * @author Nvvvy
 */
public class Commit implements Serializable {

    /**
     * The message of this Commit.
     */
    String message;
    /**
     * The map between file name and sha-1 in commit
     */
    Map<String, String> blobs;
    final String timestamp;
    List<String> parents;
    final String id;


    public Commit(String msg, Date date, List<String> p, Map<String, String> files) {
        message = msg;
        blobs = new TreeMap<>();
        timestamp = (new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US)).format(date);
        parents = p;
        blobs = files;

        id = sha1(timestamp, message, parents.get(0), parents.get(1));
    }

    public static void saveCommit(Commit c) {
        writeObject(join(OBJ_DIR, c.id), c);
    }

    public static Commit readCommit(String blobId) {
        return readObject(join(OBJ_DIR, blobId), Commit.class);
    }

    public String log() {
        String head = "===\n";
        String blobId = "commit " + id + "\n";
        String merge = parents.get(1).isEmpty() ? "" : parents.get(0).substring(0, 7) +
                " " + parents.get(1).substring(0, 7) + "\n";
        String date = "Date: " + timestamp + "\n";
        return head + blobId + merge + date + message + "\n";
    }

    @Override
    public String toString() {
        return log();
    }

    public static void printCommit(Commit c) {
        System.out.print(c.log());
    }

    public Commit getParent(int i) {
        if (parents.get(i).isEmpty()) {
            return null;
        }
        return readObject(join(OBJ_DIR, parents.get(i)), Commit.class);
    }

    @Override
    public boolean equals(Object c) {
        return c.hashCode() == this.hashCode();
    }

    @Override
    public int hashCode() {
        return Integer.parseInt(this.id);
    }

    public String getFileSha1(String fileName) {
        return blobs.get(fileName);
    }

}
