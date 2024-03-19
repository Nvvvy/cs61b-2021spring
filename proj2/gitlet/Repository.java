package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.*;



/** Represents a gitlet repository.
 *  A gitlet repo supports a limited set of commands listed in validCmd,
 *  along with a series of methods of git command.
 *
 *  .gitlet
 *      commit
 *      staged
 *      files
 *
 *  @author Nvvvy
 */
public class Repository implements Serializable {

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The directory which saves all serialized Commit objects i.e.commit records */
    static final File COMMIT_DIR = join(GITLET_DIR, "commit");
    /** The directory which saves file copies staged for addition */

    // TODO: save the files' snapshots, which should be a mapping between the sha-1 and file name
    private Map<String, String> forAddition;

    private Map<String, String> forRemoval;

    /** The mapping which uses the branch name as key and commit sha-1 id as value */
    private Map<String, String> HEAD;

    /** branches' names of repo */
    private List<String> branches;
    /** The current branch of repo */
    private String currentBranch;


    public Repository(Commit initialCommit) {
        // starts with a single master branch
        branches = new ArrayList<>();
        branches.add("master");
        currentBranch = "master";

        // set up the initial commit and head
        HEAD = new TreeMap<>();
        HEAD.put("master", initialCommit.blobId);

        // initial staging area is empty
        forAddition = new TreeMap<>();
    }

    /** Reads the repo object from .gitlet dir */
    static Repository repoFromFile() {
        File repoConfig = new File(GITLET_DIR, "repoConfig.txt");
        return readObject(repoConfig, Repository.class);
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
     * Returns the last commit of current branch
     * @param repo Repository
     * @return last commit of current branch
     */
    static Commit lastCommit(Repository repo) {
        String lastCommitId = repo.HEAD.get(repo.currentBranch);
        return Commit.loadCommit(lastCommitId);
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
        GITLET_DIR.mkdir();
        COMMIT_DIR.mkdir();

        Commit initial = new Commit("initial commit", null, new Date(0), new TreeMap<>());
        Repository repo = new Repository(initial);
        saveRepo(repo);
    }

    /**
     * Adds a copy of the file as it currently exists to the staging area
     */
    static void add(String fileName) {
        File f = join(CWD, fileName);
        if (f.isDirectory() || !f.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }
        Repository repo = repoFromFile();

        /* Spec:
        If the current working version of the file is identical to
        the version in the current commit, do not stage it to be added,
        and remove it from the staging area if it is already there
        (as can happen when a file is changed, added, and then changed back to it’s original version).*/
        // TODO: 1. calculate sha-1 of the given file
        String latestContent = readContentsAsString(f);
        String fileId = sha1(latestContent);
        // TODO: 2. get the file list of last commit by head
        Commit parent = lastCommit(repo);
        // TODO: 3. compare sha-1 of give file with last commit file list
        // TODO: 4. if same: do nothing. else: add the copy of file to staging area

        // TODO: to be modified
        if (parent.fileToBlob.containsKey(fileName)) {
            String lastVersionId = parent.fileToBlob.get(fileName);
            if (lastVersionId.equals(fileId)) {
                return;
            } else {
                // delete the staged file
                File oldVersion = join(GITLET_DIR, lastVersionId);
                oldVersion.delete();
            }
        }
        // TODO: 5. Staging an already-staged file overwrites the previous entry in the staging area with the new contents
        repo.forAddition.put(fileName, fileId);
        File latestCopy = join(GITLET_DIR,  fileId + ".txt");
        writeContents(latestCopy, latestContent);

        saveRepo(repo);
    }


    /**
     * Saves a snapshot of tracked files in the current commit and staging area,
     * then creates a new commit
     * Ignores everything (missing file, file change...) outside the .gitlet directory.
     * @param message commit log message in command
     */
    static void commit(String message) {
        Repository repo = repoFromFile();
        Commit parent = lastCommit(repo);

        // TODO:  If no files have been staged, abort. Print the message .
        if (repo.forAddition.isEmpty()) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }

        // TODO: add files in staging area (not working directory) to file snapshot of commit
        Commit newCommit = new Commit(message, parent, new Date(), repo.forAddition);

        // TODO: The staging area is cleared after a commit.
//        for (String fileId : repo.forAddition.values()) {
//            File sourFile = join(GITLET_DIR, fileId + ".txt");
//            File targetFile = join(GITLET_DIR, fileId + ".txt");
//            writeContents(targetFile, readContentsAsString(sourFile));
//            restrictedDelete(sourFile);
//        }
        repo.forAddition.clear();

        // TODO: The commit just made becomes the “current commit”, and the head pointer now points to it.
        repo.HEAD.put(repo.currentBranch, newCommit.blobId);

        saveRepo(repo);
    }

    /**
     * Unstage the file if it is currently in staging area,
     * remove the file from the working directory if the file is tracked in the current commit
     * @param fileName the name of file to be removed from repo working directory
     */
    static void rm(String fileName) {
        Repository repo = repoFromFile();
        Commit c = lastCommit(repo);
        if (c.fileToBlob.containsKey(fileName)) {
            c.fileToBlob.remove(fileName);
            File discarded = join(CWD, fileName);
            restrictedDelete(discarded);
            repo.forRemoval.put(fileName, c.blobId);
        } else if (repo.forAddition.containsKey(fileName)) {
            repo.forAddition.remove(fileName);
            File untracked = join(GITLET_DIR, fileName);
            untracked.delete();
            repo.forRemoval.put(fileName, c.blobId);
        } else {
            // file is neither staged nor tracked by the head commit
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }
    }

    /**
     * Prints the commit history of the current branch's head
     */
    static void log() {
        Repository repo = repoFromFile();
        Commit last = lastCommit(repo);
        List<Commit> history = last.firstParent();
        for (Commit c : history) {
            c.printCommit();
        }
    }

    /**
     * Prints information about all commits ever made
     */
    static void globalLog() {
        List<String> commits = plainFilenamesIn(COMMIT_DIR);
        for (String commitId : commits) {
            Commit c = Commit.loadCommit(commitId);
            c.printCommit();
        }
    }

    /**
     * Prints out the ids of all commits that have the given commit message, one per line.
     * @param m target commit message
     */
    static void find(String m) {
        List<String> commits = plainFilenamesIn(COMMIT_DIR);
        boolean found = false;
        for (String commitId : commits) {
            Commit c = Commit.loadCommit(commitId);
            if (c.message.equals(m)) {
                found = true;
                System.out.println(commitId);
            }
        }

        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    /**
     * Prints the repo status
     */
    static void status() {
        Repository repo = repoFromFile();

        System.out.println("=== Branches ===");
        for (String branch : repo.branches) {
            if (branch.equals(repo.currentBranch)) {
                branch = "*" + branch;
            }
            System.out.println(branch);
        }

        System.out.println("=== Staged Files ===");
        for (String fileName : repo.forAddition.keySet()) {
            System.out.println(fileName);
        }

        System.out.println("=== Removed Files ===");
        for (String fileName : repo.forRemoval.keySet()) {
            System.out.println(fileName);
        }

        // TODO: Modifications Not Staged For Commit
        // TODO: Untracked Files
    }


}
