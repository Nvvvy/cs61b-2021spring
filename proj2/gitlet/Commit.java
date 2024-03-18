package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Utils.*;

/**
 *  Represents a gitlet commit object.
 *  All tracked files will be listed in a one-dimensional list.
 *  In other words, there is only one "flat" directory of plain files for each repo.
 *  A commit consist of a log message, timestamp, a mapping of file names to blob references,
 *  a parent reference, and (for merges) a second parent reference.
 *
 *
 *  @author Nvvvy
 */
public class Commit implements Serializable {

    /** The message of this Commit. */
    private final String message;
    /** The reference to the parent commit, at most 2 ref */
    private final String[] parentId;
    /** the timeStamp of commit with a yy-mm-dd hh-mm-ss format */
    private final String timestamp;
    /** The sha-1 code of the commit */
    final String blobId;
    /** a mapping of file names to blob references */
    Map<String, String> fileToBlob;


    /* TODO： Stage1: init, add, commit, checkout -- [file name], checkout [commit id] -- [file name], log */
    public Commit(String message, Commit parent, Date date, Map<String, String> fileForAdd) {
        this.message = message;
        this.parentId = new String[2];
        this.parentId[0] = parent != null ? parent.blobId : "";
        this.parentId[1] = "";

        // convert Date to string
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.timestamp = sdf.format(date);

        /* Each commit is identified by its SHA-1 id,
        which must include the file (blob) references of its files,
        parent reference, log message, and commit time. */
        this.blobId = Utils.sha1(message, parentId[0], timestamp); // TODO: figure out how sha-1 generates

        // update the files mapping between file name and blobId
        this.fileToBlob = parent != null ? updateFileRef(parent, fileForAdd) : new TreeMap<>();
        saveCommit(this);
    }


    /**
     * Save a commit under the .gitlet directory
     */
    static void saveCommit(Commit c) {
        // TODO: should evey commit be under a different directory?
        File f = join(Repository.COMMIT_DIR, c.blobId);
        writeObject(f, c);
    }

    /**
     * Reads the given file and returns a Commit obj if the file refers to a commit
     * @param blobId the id of commit
     */
    static Commit loadCommit(String blobId) {
        File commitFile = join(Repository.COMMIT_DIR, blobId);
        return readObject(commitFile, Commit.class);
    }

    /**
     * Save the snapshots of files of a given commit and the staging area
     *
     * @param parent parent Commit
     * @param fileForAdd a mapping between staged file name and blobId
     */
    static Map<String, String> updateFileRef(Commit parent, Map<String, String> fileForAdd) {
        Map<String, String> newFileRef = new TreeMap<>();
        newFileRef.putAll(parent.fileToBlob);
        newFileRef.putAll(fileForAdd);
        return newFileRef;
    }

    /**
     * Returns parent commits of this commit
     * @return parent commits
     */
    List<Commit> getParents() {
        List<Commit> parents = new ArrayList<>();
        for (String blobId : parentId) {
            if (blobId.isEmpty()) {
                continue;
            }
            parents.add(loadCommit(blobId));
        }
        return parents;
    }

}
