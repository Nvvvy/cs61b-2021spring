package gitlet;

import java.io.File;
import java.io.Serializable;
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
    final String message;
    /** The reference to the parent commit, at most 2 ref */
    final String[] parentId;
    /** the timeStamp of commit with a yy-mm-dd hh-mm-ss format */
    final String timestamp;
    /** The sha-1 code of the commit */
    final String blobId;
    /** a mapping of file names to blob references */
    Map<String, String> fileToBlob;


    public Commit(String m, Commit p0, Commit p1, Date date, Map<String, String> fileForAdd, Map<String, String> fileForRm) {
        message = m;
        parentId = new String[2];
        parentId[0] = p0 != null ? p0.blobId : "";
        parentId[1] = p1 != null ? p1.blobId : "";

        timestamp = date.toString();
        blobId = Utils.sha1(message, parentId[0] + parentId[1], timestamp);

        // update the files mapping between file name and blobId
        fileToBlob = p0 != null ? updateFileRef(p0, fileForAdd, fileForRm) : new TreeMap<>();
        saveCommit(this);
    }


    /**
     * Save a commit under the .gitlet directory
     */
    static void saveCommit(Commit c) {
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
     * Returns false if there is no corresponding commit with a given blob id
     * @param commitId blob id of target commit
     */
    static boolean readCommitSuccess(String commitId) {
        try {
            File commitBlob = join(Repository.COMMIT_DIR, commitId);
            readObject(commitBlob, Commit.class);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Save the snapshots of files of a given commit and the staging area
     * @param parent parent Commit
     * @param fileForAdd a mapping between staged file name and blobId
     * @param fileForRm a mapping between unstaged file name and blobId
     */
    static Map<String, String> updateFileRef(Commit parent, Map<String, String> fileForAdd, Map<String, String> fileForRm) {
        Map<String, String> newFileRef = new TreeMap<>();
        newFileRef.putAll(parent.fileToBlob);
        newFileRef.putAll(fileForAdd);
        newFileRef.keySet().removeAll(fileForRm.keySet());
        return newFileRef;
    }

    /**
     * Returns first parent commits of this commit
     * @return parent commits
     */
    List<Commit> firstParent() {
        List<Commit> parents = new ArrayList<>();
        Commit c = this;
        while (!c.parentId[0].isEmpty()) {
            parents.add(c);
            c = loadCommit(c.parentId[0]);
        }
        parents.add(c);
        return parents;
    }

    /**
     * Prints the basic info of commit for log command
     */
    void printCommit() {
        System.out.println("===");
        System.out.println("commit " + blobId);
        // TODO: deal with Merged commit: display two parents
        if (!parentId[1].isEmpty()) {
            System.out.println("Merge: " + parentId[0].substring(0, 7)
                    + " " + parentId[1].substring(0, 7));
        }
        System.out.println("Date: " + timestamp);
        System.out.println(message + "\n");
    }

}
