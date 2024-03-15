package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static gitlet.Utils.*;

/**
 *  Represents a gitlet commit object.
 *  All tracked files will be listed in a one-dimensional list.
 *  In other words, there is only one "flat" directory of plain files for each repo.
 *  A commit consist of a log message, timestamp, a mapping of file names to blob references,
 *  a parent reference, and (for merges) a second parent reference.
 *
 *  @author Nvvvy
 */
public class Commit implements Serializable {

    /** The message of this Commit. */
    private String message;
    /** The reference to the parent commit, at most 2 ref */
    private String parentId;
    /** the timeStamp of commit with a yy-mm-dd hh-mm-ss format */
    private String timestamp;
    /** The sha-1 code of the commit */
    String blobId;
    /** a mapping of file names to blob references */
    private List<String> files;


    /* TODO: fill in the rest of this class. */
    /* Stage1: init, add, commit, checkout -- [file name], checkout [commit id] -- [file name], log */
    public Commit(String message, String parentId, Date date) {
        this.message = message;
        this.parentId = parentId; // TODO: how to get the parent of a commit

        // convert Date to string
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.timestamp = sdf.format(date);

        this.blobId = Utils.sha1(message, parentId, timestamp); // TODO: figure out how sha-1 generates
        // TODO: make the commit information persist
        saveCommit(this);
    }


    /**
     * Save a commit under the .gitlet directory
     */
    private static void saveCommit(Commit c) {
        // TODO: figure out how to save commit as a file in hard drive
        File f = join(Repository.GITLET_DIR, c.blobId + ".txt");
        writeObject(f, c);
    }

    /**
     * Reads the given file and returns a Commit obj if the file refers to a commit
     * @param f a java.io.File obj
     */
    private static Commit loadCommit(File f) {
        return readObject(f, Commit.class);
    }
}
