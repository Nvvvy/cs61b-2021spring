package gitlet;

import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.*;
import static gitlet.Repository.*;

public class Index implements Serializable {
    public Map<String, String> forAddition;
    public Set<String> forRemoval;
    public Map<String, String> headBlobs;
    public Map<String, List<String>> commitGraph;

    // staging area is the index of files in working directory, which refers to files under obj_dir
    // file name, file timestamp, file length, file type, sha-1

    public Index() {
        forAddition = new HashMap<>();
        forRemoval = new HashSet<>();
        headBlobs = new HashMap<>();
        commitGraph = new HashMap<>();
    }

    public boolean isEmpty() {
        return forAddition.isEmpty() && forRemoval.isEmpty();
    }

    public void clear() {
        forAddition.clear();
        forRemoval.clear();
    }

    public void updateGraph(String currentId, List<String> parents) {
        List<String> edges = commitGraph.get(currentId);
        for (String id : parents) {
            if (!id.isEmpty()) {
                edges.add(id);
            }
        }
        commitGraph.put(currentId, edges);
    }

    public static Index readIndex() {
        return readObject(INDEX, Index.class);
    }

    public static void saveIndex(Index stage) {
        writeObject(INDEX, stage);
    }

    public static void initIndex(String commitId) {
        Index init = new Index();
        init.commitGraph.put(commitId, List.of());
        saveIndex(init);
    }
}
