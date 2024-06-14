package gitlet;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static gitlet.Utils.*;
import static gitlet.Commit.*;
import static gitlet.MyUtils.*;
import static gitlet.Main.quitWithMsg;

/**
 * Represents a gitlet repository.
 *
 * <p> Structure of .gitlet directory: </p>
 * <p> HEAD: file that refers to the current head of repo. points to refs/head folder.</p>
 * --Objects: stores serialized blob & commit objects (tree is not included).
 * --refs: stores references. refs/heads contains pointers to branches and refs/tags contains pointers to tags.
 * --index: file which represents stage area
 *
 * @author nvvvy
 */
public class Repository {

    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /*
     *   .gitlet
     *      |--objects
     *      |     |--commit and blob
     *      |--refs
     *      |    |--heads
     *      |         |--master
     *      |--HEAD
     *      |--index
     */

    /**
     * The head pointer is the pointer at the front of the current branch.
     */
    public static final File HEAD = join(GITLET_DIR, "HEAD");

    /**
     * a mapping from branch heads to references to commits,
     * so that certain important commits have symbolic names
     */
    public static final File REFS = join(GITLET_DIR, "refs");
    public static final File HEADS_DIR = join(REFS, "heads");

    /**
     * directory contains stores Commit and Blob objects
     */
    public static final File OBJ_DIR = join(GITLET_DIR, "objects");

    /**
     * Staging area for files to be added or removed in the next commit.
     * Maintains a map between ever tracked file name and sha-1
     */
    public static final File INDEX = join(GITLET_DIR, "index");


    static boolean isInitialized() {
        return GITLET_DIR.exists() && GITLET_DIR.isDirectory();
    }

    /**
     * Exit program if the file does not exist in working directory
     *
     * @param fileName name of file
     */
    static void validateFile(String fileName) {
        if (!join(CWD, fileName).exists()) {
            quitWithMsg("File does not exist.");
        }
    }

    /**
     * Initialize repo, create folders
     */
    public static void init() {
        // create directories
        OBJ_DIR.mkdirs();
        HEADS_DIR.mkdirs();

        Commit initCommit = new Commit("initial commit", new Date(0), List.of("", ""), new TreeMap<>());
        saveCommit(initCommit);

        // update refs and HEAD
        writeContents(HEAD, "refs/heads/master");
        writeContents(join(HEADS_DIR, "master"), initCommit.id);

        // init Index
        Index.initIndex(initCommit.id);
    }

    public static void add(String fileName) {
        validateFile(fileName);

        Index stage = Index.readIndex();
        Blob toBeAdded = new Blob(fileName);
        String contentSha1 = toBeAdded.getSha1();
        String lastCommitSha1 = headCommit().blobs.get(fileName);

        if (contentSha1.equals(lastCommitSha1)) {
            // If the current working version of the file is identical to the version in the current commit,
            // remove it from the staging area if it is already there
            stage.forAddition.remove(fileName);
            System.exit(0);
        }

        String lastStagedSha1 = stage.forAddition.get(fileName);
        if (lastStagedSha1 != null) {
            if (contentSha1.equals(lastStagedSha1)) {
                System.exit(0);
            }
            safeDelObj(join(OBJ_DIR, lastStagedSha1));
        }

        stage.forAddition.put(fileName, contentSha1);
        stage.forRemoval.remove(fileName);
        Blob.saveBlob(toBeAdded); //put blob into OBJ_DIR
        Index.saveIndex(stage);
    }

    /**
     * @return the HEAD commit
     */
    static Commit headCommit() {
        return readCommit(headSha1());
    }

    static String headSha1() {
        File targetCommit = join(HEADS_DIR, activeBranch());
        return readContentsAsString(targetCommit);
    }

    static String activeBranch() {
        String[] ref = readContentsAsString(HEAD).split("/");
        return ref[ref.length - 1];
    }

    public static void naiveCommit(String message) {
        Index stage = Index.readIndex();
        if (stage.isEmpty()) {
            quitWithMsg("No changes added to the commit.");
        }
        Commit head = headCommit();
        commit(message, stage, head, null);
    }

    private static void commit(String message, Index stage, Commit parent1, Commit parent2) {
        // update blobs staged for addition
        Map<String, String> blobs = new TreeMap<>(parent1.blobs);
        blobs.putAll(stage.forAddition);
        stage.forRemoval.forEach(blobs::remove);

        List<String> parents = new ArrayList<>(List.of(parent1.id));
        if (parent2 != null) {
            parents.add(parent2.id);
        }

        Commit newHead = new Commit(message, new Date(), parents, blobs);
        saveCommit(newHead);
        // clear staging area after commit & update commit graph
        stage.clear();
        stage.headBlobs = blobs;
        stage.updateGraph(newHead.id, newHead.parents);
        Index.saveIndex(stage);

        // update HEAD: modify refs/heads/master
        writeContents(join(HEADS_DIR, activeBranch()), newHead.id);
    }

    public static void rm(String file) {
        Index stage = Index.readIndex();
        if (!stage.headBlobs.containsKey(file) || stage.forAddition.remove(file) == null) {
            quitWithMsg("No reason to remove the file.");
        }

        stage.forRemoval.add(file);
        restrictedDelete(file);
        Index.saveIndex(stage);
    }

    public static void log() {
        Commit head = headCommit();
        while (head != null) {
            System.out.println(head);
            head = head.getParent(0);
        }
    }

    public static void globalLog() {
        Index stage = Index.readIndex();
        stage.commitGraph.keySet().stream().map(Commit::readCommit).forEach(Commit::printCommit);
    }

    public static void find(String message) {
        Index stage = Index.readIndex();
        Set<Commit> filtered = stage.commitGraph.keySet().stream().map(Commit::readCommit).
                filter(msg -> msg.equals(message)).collect(Collectors.toSet());
        if (filtered.isEmpty()) {
            quitWithMsg("Found no commit with that message.");
        }
        filtered.forEach(commit -> System.out.println(commit.id));
    }

    public static Set<String> untrackedFiles() {
        Index stage = Index.readIndex();
        return plainFilenamesIn(CWD).stream().filter(file -> !stage.headBlobs.containsKey(file) ||
                        !stage.forRemoval.contains(file) ||
                        !stage.forAddition.containsKey(file)).
                collect(Collectors.toSet());
    }


    public static void status() {
        Index stage = Index.readIndex();
        Set<String> allFiles = new HashSet<>(plainFilenamesIn(CWD));

        System.out.println("=== Branches ===");
        plainFilenamesIn(HEADS_DIR).stream().
                map(branch -> activeBranch().equals(branch) ? "*" + branch : branch).
                forEach(System.out::println);

        System.out.println("\n=== Staged Files ===");
        stage.forAddition.keySet().forEach(System.out::println);

        System.out.println("\n=== Removed Files ===");
        stage.forRemoval.forEach(System.out::println);

        // TODO: Extra credits
        System.out.println("\n=== Modifications Not Staged For Commit ===");

        System.out.println("\n=== Untracked Files ===");
        untrackedFiles().forEach(System.out::println);
    }

    private static Commit getBranchHead(String branch) {
        String blobId = readContentsAsString(join(HEADS_DIR, branch));
        return readCommit(blobId);
    }

    private static String getFullCommitId(String id) {
        String sha1 = plainFilenamesIn(OBJ_DIR).stream().
                filter(blobId -> blobId.startsWith(id)).limit(1).toString();
        if (sha1 == null) {
            quitWithMsg("No commit with that id exists.");
        }
        return sha1;
    }

    public static void checkout(String file) {
        checkoutFile(headCommit(), file);
    }

    public static void checkout(String file, String commitId) {
        String sha1 = getFullCommitId(commitId);
        checkoutFile(readCommit(sha1), file);
    }

    public static void checkoutFile(Commit c, String file) {
        if (!c.blobs.containsKey(file)) {
            quitWithMsg("File does not exist in that commit.");
        }
        restrictedDelete(file);
        File target = join(OBJ_DIR, c.blobs.get(file));
        writeContents(join(CWD, file), readContentsAsString(target));
    }

    public static void checkoutBranch(String branch) {
        if (!plainFilenamesIn(HEADS_DIR).contains(branch)) {
            quitWithMsg("No such branch exists.");
        } else if (branch.equals(activeBranch())) {
            quitWithMsg("No need to checkout the current branch.");
        }

        Commit targetCommit = getBranchHead(branch);
        checkoutCommit(targetCommit);

        // update HEAD
        writeContents(HEAD, "refs/heads/" + branch);
    }


    private static void checkoutCommit(Commit c) {
        if (new HashSet<>(c.blobs.keySet()).retainAll(untrackedFiles())) {
            // a working file is untracked in the current branch and would be overwritten by the checkout
            quitWithMsg("There is an untracked file in the way;" +
                    " delete it, or add and commit it first.");
        }

        // checkout files
        Index stage = Index.readIndex();
        c.blobs.forEach((fileName, sha1) -> checkoutFile(c, fileName));
        plainFilenamesIn(CWD).stream().filter(file -> stage.headBlobs.containsKey(file) &&
                !c.blobs.containsKey(file)).forEach(Utils::restrictedDelete);

        // update stage
        stage.headBlobs = c.blobs;
        stage.clear();
        Index.saveIndex(stage);
    }

    public static void branch(String tag) {
        if (plainFilenamesIn(HEADS_DIR).contains(tag)) {
            quitWithMsg("A branch with that name already exists.");
        }
        String headSha1 = readContentsAsString(join(HEADS_DIR, activeBranch()));
        writeContents(join(HEADS_DIR, tag), headSha1);
    }

    private static void checkBranch(String tag) {
        if (!plainFilenamesIn(HEADS_DIR).contains(tag)) {
            quitWithMsg("A branch with that name does not exist.");
        }
    }

    public static void rmBranch(String tag) {
        checkBranch(tag);
        if (activeBranch().equals(tag)) {
            quitWithMsg("Cannot remove the current branch.");
        }
        join(HEADS_DIR, tag).delete();
    }

    public static void reset(String id) {
        String sha1 = getFullCommitId(id);
        checkoutCommit(readCommit(sha1));

        // moves the current branch’s head to that commit node
        writeContents(join(OBJ_DIR, activeBranch()), sha1);
    }

    /**
     * Merges files from the given branch into the current branch.
     *
     * @param branch branch to be merged
     */
    public static void merge(String branch) {
        checkBranch(branch);
        Index stage = Index.readIndex();
        if (!stage.isEmpty()) {
            quitWithMsg("You have uncommitted changes.");
        } else if (activeBranch().equals(branch)) {
            quitWithMsg("Cannot merge a branch with itself.");
        }

        Commit currentHead = headCommit(), branchHead = getBranchHead(branch);
        Commit mergeBase = splitPoint(stage.commitGraph, currentHead.id, branchHead.id);

        if (branchHead.equals(mergeBase)) {
            // the split point is the same commit as the given branch
            quitWithMsg("Given branch is an ancestor of the current branch.");
        } else if (currentHead.equals(mergeBase)) {
            // the split point is the current branch, then check out the given branch
            checkoutCommit(branchHead);
            quitWithMsg("Current branch fast-forwarded.");
        }

        String message = "Merged " + "branch" + " into" + activeBranch();
        merge(message, mergeBase, currentHead, branchHead, stage);
    }

    public static void merge(String message, Commit base, Commit curr, Commit given, Index stage) {
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(base.blobs.keySet());
        allFiles.addAll(curr.blobs.keySet());
        allFiles.addAll(given.blobs.keySet());

        boolean conflict = false;
        for (String f : allFiles) {
            switch (mergeStrategy(f, base, curr, given)) {
                case 1: // remove and untracked
                    stage.forRemoval.add(f);
                    restrictedDelete(f);
                    break;
                case 2: // checkout and stage
                    checkoutFile(given, f);
                    stage.forAddition.put(f, given.getFileSha1(f));
                    break;
                case 3: // conflict handling
                    String currContent = readContentsAsString(join(OBJ_DIR, curr.getFileSha1(f)));
                    String givenContent = readContentsAsString(join(OBJ_DIR, given.getFileSha1(f)));
                    String overWrite = "<<<<<<< HEAD\n" + currContent + "=======\n" + givenContent + ">>>>>>>";
                    writeContents(join(CWD, f));
                    stage.forAddition.put(f, Blob.contentSha1(overWrite));
                    conflict = true;
                    break;
                default: // do nothing
                    break;
            }
        }

        commit(message, stage, curr, given);
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    private static int mergeStrategy(String f, Commit base, Commit curr, Commit given) {
        // 4: do nothing, 1: remove and untracked 2: checkout and stage 3: conflict

        String other = given.getFileSha1(f);
        String now = curr.getFileSha1(f);
        String source = base.getFileSha1(f);

        if (source == null) {
            if (other == null) {
                return 4; // not present at the split point && present only in the current branch -> unchanged
            } else if (now == null) {
                return 2; // not present at the split point && present only in the given branch -> checkout && stage
            } else if (now.equals(other)) {
                return 4; // both modified -> unchanged
            } else {
                return 3; // conflict: modified in different ways -> rewrite
            }
        } else {
            if (other == null && now == null) {
                return 4; // both removed -> unchanged
            }
            if (source.equals(now) && other == null) {
                // present at the split point, unmodified in the current branch, absent in the given branch -> remove and untracked
                return 1;
            }
            if (source.equals(other) && now == null) {
                // present at the split point, unmodified in the given branch, and absent in the current branch -> do nothing
                return 4;
            }
            if (source.equals(now) && !source.equals(other)) {
                // only modified in the given branch -> checkout given branch file && stage
                return 2;
            }
            if (source.equals(other) && !source.equals(now)) {
                return 4; // only modified in current branch -> do nothing
            }
            if (!now.equals(other)) {
                return 3; // conflict: modified in different ways -> rewrite
            }
        }
        return 0;
    }


    /**
     * Runs BFS on commitGraph to find latest common ancestor of commit a and b
     *
     * @param a sha1 id of commit
     * @param b sha1 id of commit
     * @return merge-base commit of a and b
     */
    private static Commit splitPoint(Map<String, List<String>> commitGraph, String a, String b) {
        Set<String> ancestorsA = new HashSet<>(), ancestorsB = new HashSet<>();
        Queue<String> queueA = new LinkedList<>(), queueB = new LinkedList<>();
        queueA.add(a);
        queueB.add(b);

        while (!queueA.isEmpty() || !queueB.isEmpty()) {
            if (!queueA.isEmpty()) {
                String currentA = queueA.poll();
                if (ancestorsB.contains(currentA)) {
                    return readCommit(currentA);
                }
                ancestorsA.add(currentA);
                List<String> parentsA = commitGraph.get(currentA);
                if (parentsA != null) {
                    queueA.addAll(parentsA);
                }
            }

            if (!queueB.isEmpty()) {
                String currentB = queueB.poll();
                if (ancestorsA.contains(currentB)) {
                    return readCommit(currentB);
                }
                ancestorsB.add(currentB);
                List<String> parentB = commitGraph.get(currentB);
                if (parentB != null) {
                    queueB.addAll(parentB);
                }
            }
        }
        return null;
    }
}
