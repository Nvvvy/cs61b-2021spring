package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.*;
import static gitlet.Commit.*;



/**
 *  A gitlet repo deals with plain text files, subdirectory and non-text file not included
 *  @author Nvvvy
 */
public class Repository implements Serializable {

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The directory which saves all serialized Commit objects i.e.commit records */
    static final File COMMIT_DIR = join(GITLET_DIR, "commit");
    /** The directory which blob files (use blob id as name, not extension name) */
    static final File BLOB_DIR = join(GITLET_DIR, "blob");

    /** The mapping for file name and blob id of files staged for addition */
    private final Map<String, String> forAddition;
    /** The mapping for file name and blob id of files staged for removal */
    private final Map<String, String> forRemoval;
    /** The mapping which uses the branch name as key and commit blob id as value */
    private final Map<String, String> refs;
    /** The 40-bits blob id of the last commit of the active branch */
    private String HEAD;


    public Repository(Commit initialCommit) {
        // starts with a single master branch
        HEAD = initialCommit.blobId;
        refs = new TreeMap<>();
        refs.put("master", HEAD);

        // initial staging area is empty
        forAddition = new TreeMap<>();
        forRemoval = new TreeMap<>();
    }

    /**
     * Untracked files are files in CWD that were not tracked by given commit
     * and are not in staging area.
     * @param c given commit
     * @return A set of untracked files in CWD.
     */
    Set<String> untrakedFiles(Commit c) {
        // TODO: does a file ever tracked by a commit is called tracked?
        Set<String> all = new TreeSet<>(plainFilenamesIn(CWD));
        all.removeAll(c.fileToBlob.keySet());
        all.retainAll(forAddition.keySet());
        all.addAll(forRemoval.keySet());
        return all;
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
     */
    static boolean inGitWorkingDirectory() {
        File gitDir = join(new File(System.getProperty("user.dir")), ".gitlet");
        return gitDir.exists() && gitDir.isDirectory();
    }

    /**
     * Returns the last commit of current branch
     * @param repo Repository
     * @return last commit of current branch
     */
    static Commit headCommit(Repository repo) {
        return loadCommit(repo.HEAD);
    }

    /**
     * Set up the directories for gitlet
     */
    static void init() {
        // check whether cwd is already under a git working directory
        if (inGitWorkingDirectory()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        GITLET_DIR.mkdir();
        COMMIT_DIR.mkdir();
        BLOB_DIR.mkdir();

        Commit initial = new Commit("initial commit", null, null, new Date(0), new TreeMap<>(), new TreeMap<>());
        Repository repo = new Repository(initial);
        saveRepo(repo);
    }

    /**
     * A blob file/copy will be added to BLOB_DIR only if the current version
     * is different from the HEAD version.
     */
    static void add(String fileName) {
        File f = join(CWD, fileName);
        if (f.isDirectory() || !f.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }

        Repository repo = repoFromFile();
        String latestContent = readContentsAsString(f);
        String fileId = sha1(latestContent);
        Commit HEAD = headCommit(repo);

        if (HEAD.fileToBlob.containsKey(fileName)) {
            String lastVersionId = HEAD.fileToBlob.get(fileName);
            join(BLOB_DIR, lastVersionId).delete();
            if (lastVersionId.equals(fileId)) {
                // the HEAD version is identical to current file
                return;
            }
        }
        // Staging an already-staged file overwrites the previous entry in the staging area with the new contents
        repo.forAddition.put(fileName, fileId);
        File latestCopy = join(BLOB_DIR,  fileId);
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
        Commit parent = headCommit(repo);

        if (repo.forAddition.isEmpty()) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }

        // add files in staging area (not working directory) to file snapshot of commit
        Commit newCommit = new Commit(message, parent, null, new Date(), repo.forAddition, repo.forRemoval);
        // staging area is cleared after a commit.
        repo.forAddition.clear();
        // The commit just made becomes HEAD
        repo.HEAD= newCommit.blobId;

        saveRepo(repo);
    }

    /**
     * Unstage the file if it is currently in staging area,
     * remove the file from the working directory if the file is tracked in the current commit
     * @param fileName the name of file to be removed from repo working directory
     */
    static void rm(String fileName) {
        Repository repo = repoFromFile();
        Commit c = headCommit(repo);
        if (c.fileToBlob.containsKey(fileName)) {
            repo.forRemoval.put(fileName, c.fileToBlob.get(fileName));
            File untracked = join(CWD, fileName);
            restrictedDelete(untracked);
        } else if (repo.forAddition.containsKey(fileName)) {
            File unstaged = join(CWD, repo.forAddition.get(fileName));
            repo.forAddition.remove(fileName);
            unstaged.delete();
        } else {
            // file is neither staged nor tracked by the head commit
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }
        saveRepo(repo);
    }

    /**
     * Prints the commit history of the current branch's head
     */
    static void log() {
        Repository repo = repoFromFile();
        Commit last = headCommit(repo);
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
            Commit c = loadCommit(commitId);
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
            Commit c = loadCommit(commitId);
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
        for (String branch : repo.refs.keySet()) {
            if (repo.refs.get(branch).equals(repo.HEAD)) {
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

        System.out.println("=== Untracked Files ===");
        for (String fileName : repo.untrakedFiles(loadCommit(repo.HEAD))) {
            System.out.println(fileName);
        }
    }

    /**
     * 3 possible cases for checkout command
     * @param args all command input
     */
    static void checkout(String[] args) {
        Repository repo = repoFromFile();
        switch (args.length) {
            case 2:
                // checkout [branch name]
                checkoutBranch(repo, args[1]);
                break;
            case 3:
                // checkout -- [file name]
                checkoutFile(args[2], headCommit(repo));
            case 4:
                // checkout [commit id] -- [file name]
                verifyCommit(args[1]);
                checkoutFile(args[3], loadCommit(args[1]));
                break;
        }
        saveRepo(repo);
    }

    /**
     * Exits the program if the given commit blob id is not valid
     * @param blobId commit blob id
     */
    private static void verifyCommit(String blobId) {
        if (!Commit.readCommitSuccess(blobId)) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
    }

    /**
     * Exits the program if given file name is not tracked by given commit
     * @param fileName name of file
     * @param c Commit obj
     */
    private static void verifyFile(String fileName, Commit c) {
        if (!c.fileToBlob.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
    }

    /**
     * Takes the version of the file as it exists in the commit with the given id,
     * and puts it in the working directory, overwriting the version of the file
     * that’s already there if there is one.
     * @param fileName name of target file
     * @param c target commit
     */
    static void checkoutFile(String fileName, Commit c) {
        verifyFile(fileName, c);
        restrictedDelete(join(CWD, fileName));

        // put the target new file under CWD
        String fileId = c.fileToBlob.get(fileName);
        File source = join(BLOB_DIR, fileId);
        File target = join(CWD, fileName);
        writeContents(target, readContentsAsString(source));
    }

    /**
     * Takes all files in the commit at the head of the given branch,
     * overwriting the versions of the files in the working directory.
     * @param branch branch name
     */
    static void checkoutBranch(Repository repo, String branch) {
        if (!repo.refs.containsKey(branch)) {
            System.out.println("No such branch exists.");
        } else if (repo.refs.get(branch).equals(repo.HEAD)) {
            System.out.println("No need to checkout the current branch.");
        } else {
            checkoutCommit(repo, repo.refs.get(branch));
        }
    }

    /**
     * A helper method for checkout [branch name] and reset [commit id]
     * Checks out all the files tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Also moves the current branch’s head to that commit node
     * @param repo target repo
     * @param commitId given commit blob id
     */
    private static void checkoutCommit(Repository repo, String commitId) {
        Commit newHead = loadCommit(commitId);
        Commit head = headCommit(repo);

        // the intersection of untracked files in HEAD and files tracked by give branch
        Set<String> intersection = repo.untrakedFiles(head);
        intersection.retainAll(newHead.fileToBlob.keySet());
        if (!intersection.isEmpty()) {
            System.out.println("There is an untracked file in the way; " +
                    "delete it, or add and commit it first.");
            System.exit(0);
        }

        for (String fileName : plainFilenamesIn(CWD)) {
            if (newHead.fileToBlob.containsKey(fileName)) {
                checkoutFile(fileName, newHead);
            } else if (head.fileToBlob.containsKey(fileName)) {
                restrictedDelete(join(CWD, fileName));
            }
        }

        repo.HEAD = newHead.blobId;
        repo.forAddition.clear();
        repo.forRemoval.clear();
    }

    /**
     * Checks out all the files tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Also moves the current branch’s head to that commit node.
     * @param commitId target commit sha-1 blobId
     */
    static void reset(String commitId) {
        verifyCommit(commitId);
        Repository repo = repoFromFile();
        checkoutCommit(repo, commitId);
        saveRepo(repo);
    }

    /**
     * Creates a new branch with the given name, and points it at the current head commit
     * @param branch new branch name, which is a name for a reference to a commit
     */
    static void branch(String branch) {
        Repository repo = repoFromFile();
        if (repo.refs.containsKey(branch)) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        repo.refs.put(branch, repo.HEAD);
        saveRepo(repo);
    }

    /**
     * Deletes the branch with the given name
     * @param branch the name of branch pointe
     */
    static void rmBranch(String branch) {
        Repository repo = repoFromFile();
        if (!repo.refs.containsKey(branch)) {
            System.out.println("A branch with that name does not exist.");
        } else if (repo.refs.get(branch).equals(repo.HEAD)) {
            System.out.println("Cannot remove the current branch.");
        } else {
            repo.refs.remove(branch);
            saveRepo(repo);
        }
    }

    /**
     * A helper method returns the latest common ancestor of a and b on commit tree
     * @param repo the Repo object
     * @param a blob id of commit
     * @param b blob id of another commit
     * @return blob id of split point
     */
    private static String splitPoint(Repository repo, String a, String b) {
        Commit x = loadCommit(a);
        Commit y = loadCommit(b);

        return "";
    }

    /**
     * Merges files from the given branch into the current branch.
     * @param branch source branch name
     */
    static void merge(String branch) {
        // TODO: search for the split point of active branch and given branch
        // special case 1: the split point (latest common ancestor) is the same commit as the given branch: do nothing
        Repository repo = repoFromFile();
        String given = repo.refs.get(branch);
        String splitPoint = splitPoint(repo, repo.HEAD, given);
        if (splitPoint.equals(given)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            System.exit(0);
        }
        // special case 2: the split point is the current branch
        if (splitPoint.equals(repo.HEAD)) {
            System.out.println("Current branch fast-forwarded.");
            checkoutBranch(repo, branch);
            System.exit(0);
        }

        /*
        Structure
        set common = set(source) && set(current)
        set ancestor = set(split point)
        */
        // deal with merge, base version: file of split point
        // 1. modified in Other but not in HEAD -> Other

        // 2. modified in HEAD, not in Other -> HEAD

        // 3. both modified, but with same content or both removed -> keep still, do not delete file

        // 4. not in split point but only in current branch -> file remains

        // 5. in split point but only in given branch -> checkout and stage

        // 6. in split point, unmodified in the current branch, and absent in the given branch -> remove and untrack

        // 7. present at the split point, unmodified in the given branch, and absent in the current branch -> remain absent

        // 8. both modified (in conflict) -> keep both content in different sections of the same file, stage

        // TODO: merge automatically commits with the log message Merged [given branch name] into [current branch name].
        // TODO: prints: Encountered a merge conflict. if there is a conflict
        // new commit has two parents: first-current branch, second-given branch
    }

}
