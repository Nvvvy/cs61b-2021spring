package gitlet;

import java.util.Set;

import static gitlet.Repository.*;

/**
 * Driver class for Gitlet, a subset of the Git version-control system.
 * Parse command line args
 *
 * @author nvvvy
 */
public class Main {

    private static Set<String> validCmd =
            Set.of("add", "commit", "rm", "log", "global-log", "find",
                    "status", "checkout", "branch", "rm-branch", "reset", "merge");

    /**
     * Usage: java gitlet.Main ARGS, where ARGS contains
     * <COMMAND> <OPERAND1> <OPERAND2> ...
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            quitWithMsg("Please enter a command.");
        }

        String firstArg = args[0];

        if (validCmd.contains(firstArg) && !isInitialized()) {
            quitWithMsg("Not in an initialized Gitlet directory.");
        }

        switch (firstArg) {
            case "init":
                // handle the `init` command
                if (isInitialized()) {
                    quitWithMsg("A Gitlet version-control system already exists in the current directory.");
                }
                validateArgs(args, 1);
                init();
                break;
            case "add":
                // handle the `add [filename]` command
                validateArgs(args, 2);
                add(args[1]);
                break;
            case "commit":
                // handle the `commit [message]` command
                if (args.length == 1) {
                    quitWithMsg("Please enter a commit message.");
                }
                validateArgs(args, 2);
                naiveCommit(args[1]);
                break;
            case "rm":
                validateArgs(args, 2);
                rm(args[1]);
                break;
            case "log":
                validateArgs(args, 1);
                log();
                break;
            case "global-log":
                validateArgs(args, 1);
                globalLog();
                break;
            case "find":
                validateArgs(args, 2);
                find(args[1]);
                break;
            case "status":
                validateArgs(args, 1);
                status();
                break;
            case "checkout":
                if (args.length == 2) { // checkout branch
                    checkoutBranch(args[1]);
                } else if (args[1].equals("--")) { // checkout -- [file name]
                    validateArgs(args, 3);
                    checkout(args[2]);
                } else if (args[2].equals("--")) { // checkout [commit id] -- [file name]
                    validateArgs(args, 4);
                    checkout(args[3], args[1]);
                }
                break;
            case "branch":
                validateArgs(args, 2);
                branch(args[1]);
                break;
            case "rm-branch":
                validateArgs(args, 2);
                rmBranch(args[1]);
                break;
            case "reset":
                validateArgs(args, 2);
                reset(args[1]);
                break;
            case "merge":
                validateArgs(args, 2); // merge [branch name]
                merge(args[1]);
                break;
            default:
                quitWithMsg("No command with that name exists.");
                break;
        }
    }

    private static void validateArgs(String[] args, int n) {
        if (args.length != n) {
            quitWithMsg("Incorrect operands.");
        }
    }

    static void quitWithMsg(String errorMsg) {
        System.out.println(errorMsg);
        System.exit(0);
    }

}
