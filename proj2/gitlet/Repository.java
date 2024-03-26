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
     * @return active branch name
     */
    String activeBranch() {
        String activeBranch = "master";
        for (String branch : forRemoval.keySet()) {
            if (forRemoval.get(branch).equals(HEAD)) {
                activeBranch = branch;
            }
        }
        return activeBranch;
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
        List<Commit> history = last.firstAncestor();
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
        if (!readCommitSuccess(blobId)) {
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
     *
     * @param a blob id of commit
     * @param b blob id of another commit
     * @return blob id of split point
     */
    private static Set<Commit> splitPoint(String a, String b) {
        Commit A = loadCommit(a);
        Commit B = loadCommit(b);
        Set<Commit> groupA = new HashSet<>();
        Set<Commit> groupB = new HashSet<>();
        groupA.add(A);
        groupB.add(B);
        Deque<Commit> queueA = new ArrayDeque<>();
        Deque<Commit> queueB = new ArrayDeque<>();
        queueA.push(A);
        queueB.push(B);
        while (!queueA.isEmpty() || !queueB.isEmpty()) {
            Set<Commit> intersection = new HashSet<>(groupA);
            intersection.retainAll(groupB);
            if (!intersection.isEmpty()) {
                return intersection;
            }
            update(queueA, groupA);
            update(queueB, groupB);
        }
        return groupA;
    }

    private static void update(Deque<Commit> q, Set<Commit> s) {
        for (Commit c : q) {
            q.pollFirst();
            List<Commit> parents = c.parents();
            q.addAll(parents);
            s.addAll(parents);
        }
    }

    private static void checkBeforeMerge(Repository repo, String branch) {
        if (!repo.refs.containsKey(branch)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        if (repo.refs.get(branch).equals(repo.HEAD)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
        if (!repo.forAddition.isEmpty() || !repo.forRemoval.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }
        Set<Commit> splitPoint = splitPoint(repo.HEAD, repo.refs.get(branch));
        Commit head = loadCommit(repo.HEAD);
        Commit other = loadCommit(repo.refs.get(branch));

        // TODO: an untracked file in the current commit would be overwritten or deleted by the merge
        if (!repo.untrakedFiles(head).isEmpty()) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            System.exit(0);
        }
        if (splitPoint.contains(other)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            System.exit(0);
        }
        // special case 2: the split point is the current branch
        if (splitPoint.contains(head)) {
            System.out.println("Current branch fast-forwarded.");
            checkoutBranch(repo, branch);
            System.exit(0);
        }
    }

    /**
     * Merges files from the given branch into the current branch.
     * @param branch source branch name
     */
    static void merge(String branch) {
        Repository repo = repoFromFile();
        checkBeforeMerge(repo, branch);

        String given = repo.refs.get(branch);
        Set<Commit> splitPoint = splitPoint(repo.HEAD, given);
        Commit head = loadCommit(repo.HEAD);
        Commit other = loadCommit(given);

        Commit commonAncestor = splitPoint.iterator().next();
        Set<String> allFiles = new TreeSet<>();
        allFiles.addAll(commonAncestor.fileToBlob.keySet());
        allFiles.addAll(head.fileToBlob.keySet());
        allFiles.addAll(other.fileToBlob.keySet());
        boolean conflict = false;

        for (String fileName : allFiles) {
            int status = compareFiles(fileName, head, other, commonAncestor);
            File curr = head.fileToBlob.containsKey(fileName) ? join(BLOB_DIR, head.fileToBlob.get(fileName)) : null;
            File another = other.fileToBlob.containsKey(fileName) ? join(BLOB_DIR, other.fileToBlob.get(fileName)) : null;
            switch (status) {
                case 3:
                    rm(fileName);
                    break;
                case 2:
                    conflict = true;
                    mergeContent(fileName, curr, another);
                    break;
                case 1:
                    checkoutFile(fileName, other);
                    break;
                case 0:
                    break;
            }
        }

        // new commit has two parents: first-current branch, second-given branch
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
        String activeBranch = repo.activeBranch();
        Commit newHead = new Commit("Merged" + branch + "into" + activeBranch,
                head, other, new Date(), repo.forAddition, repo.forRemoval);
        repo.HEAD = newHead.blobId;
        repo.refs.put(activeBranch, newHead.blobId);
        repo.forAddition.clear();
        repo.forRemoval.clear();
    }

    /**
     * Merge two conflict files' content with a single file, then creates a file in CWD
     * @param current file tracked by HEAD, could be null if deleted
     * @param other file tracked by another branch to be merged, could be null
     */
    private static void mergeContent(String fileName, File current, File other) {
        File merged = join(CWD, fileName);
        // deal with the deleted file: use empty string as content
        String contentA = current != null ? readContentsAsString(current) + "\n" : "";
        String contentB = other != null? readContentsAsString(other) + "\n" : "";
        String mergedContent = "<<<<<<< HEAD\n" + contentA + "=======\n" + contentB + ">>>>>>>\n";
        writeContents(merged, mergedContent);
    }

    private static int compareFiles(String fileName, Commit head, Commit given, Commit ancestor) {
        String curr = head.fileToBlob.getOrDefault(fileName, "");
        String other = given.fileToBlob.getOrDefault(fileName, "");
        String base = ancestor.fileToBlob.getOrDefault(fileName, "");

        // 0 - do nothing: no difference or same modification or only in current branch
        // 1 - check out file from given branch: only modified in given branch
        // 2 - merge content: conflict / modified in the different way
        // 3 - rm from HEAD: unmodified in HEAD, absent in given branch

        if (curr.equals(base) && !base.isEmpty() && other.isEmpty()) {
            return 3;
        } else if (!base.isEmpty() && !curr.equals(base)) {
            return 2;
        } else if (base.equals(other) && !base.equals(curr)) {
            return 1;
        }
        return 0;
    }
}