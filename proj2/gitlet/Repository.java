package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.*;



/** Represents a gitlet repository.
 *  A gitlet repo supports a limited set of commands listed in validCmd,
 *  along with a series of methods of git command.
 *
 *  @author Nvvvy
 */
public class Repository implements Serializable {

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    // TODO: save the files' snapshots
    private static List<File> stagingArea;

    /** A mapping which uses the branch name as key and commit sha-1 id as value */
    private Map<String, String> HEAD;

    /** branches' names of repo */
    private List<String> branches;

    private String currentBranch;

    /* TODO: fill in the rest of this class. */

    public Repository(Commit initialCommit) {
        // starts with a single master branch
        branches = new ArrayList<>();
        branches.add("master");
        currentBranch = "master";

        // set up the initial commit and head
        HEAD = new HashMap<>();
        HEAD.put("master", initialCommit.blobId);

        // initial staging area is empty
        stagingArea = new ArrayList<>();
    }

    /** Reads the repo object from a given */
    static Repository repoFromFile(String currDir) {
        File repo = join(CWD, currDir);
        return readObject(repo, Repository.class);
    }

    /** Save repo info in the repoConfig.txt under .gitlet dir */
    static void saveRepo(Repository repo) {
        File repoInfo = new File(GITLET_DIR, "repoConfig.txt");
        writeObject(repoInfo, repo);
    }


    /**
     * A helper method which returns true if current directory is in an initialized
     * Gitlet working directory (i.e., one containing a .gitlet subdirectory)
     * @param currDir the current working directory followed by command.
     */
    static boolean inGitWorkingDirectory(File currDir) {
        File rootDir = File.listRoots()[0];
        File gitDir = join(currDir, ".gitlet");

        if (gitDir.exists() && gitDir.isDirectory()) {
            return true;
        } else if (currDir.equals(rootDir)) {
            return false;
        }
        return inGitWorkingDirectory(currDir.getParentFile());
    }


    /**
     * Set up the directories for gitlet
     * maybe for git init command
     */
    static void init() {
        // check whether cwd is already under a git working directory
        if (inGitWorkingDirectory(CWD)) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }

        if (!GITLET_DIR.exists()) {
            GITLET_DIR.mkdir();
        }

        Commit initial = new Commit("initial commit", "", new Date(0));
        Repository repo = new Repository(initial);
        saveRepo(repo);
    }

    /**
     * Adds a copy of the file as it currently exists to the staging area
     */
    static void add(String file) {
        File f = join(CWD, file);
        if (f.isDirectory() || !f.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }
        // TODO: add file to staging area
        Repository repo = repoFromFile(CWD.getPath());

        repo.stagingArea.add(f);
        saveRepo(repo);
    }



    static void commit(String message) {

    }
}
