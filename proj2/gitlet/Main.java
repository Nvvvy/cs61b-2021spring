package gitlet;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Nvvvy
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }

        String firstArg = args[0];
        switch(firstArg) {
            case "init":
                validateArgs("init", args, 1);
                Repository.init();
                break;
            case "add":
                validateArgs("add", args, 2);
                Repository.add(args[1]);
                break;
            case "commit":
                validateArgs("commit", args, 2);
                Repository.commit(args[1]);
                break;
            case "rm":
                validateArgs("rm", args, 2);
                Repository.rm(args[1]);
                break;
            case "log":
                validateArgs("log", args, 1);
                Repository.log();
                break;
            case "global-log":
                validateArgs("global-log", args, 1);
                Repository.globalLog();
                break;
            case "find":
                validateArgs("find", args, 2);
                Repository.find(args[1]);
                break;
            case "status":
                validateArgs("status", args, 1);
                Repository.status();
                break;
            case "checkout":
                Repository.checkout(args);
                break;
            case "branch":
                validateArgs("branch", args, 2);
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                validateArgs("rm-branch", args, 2);
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                validateArgs("reset", args, 2);
                Repository.reset(args[1]);
                break;
            case "merge":
                validateArgs("merge", args, 2);
                Repository.merge(args[1]);
                break;
        }
    }


    /**
     * Checks the number of arguments versus the expected number,
     * prints an error message if any of these conditions is met.
     *
     * @param cmd Name of command you are validating
     * @param args Argument array from command line
     * @param n Number of expected arguments
     */
    public static void validateArgs(String cmd, String[] args, int n) {
        HashSet<String> validCmd = new HashSet<>(Set.of("init", "add", "commit", "rm", "log",
                "global-log", "find", "status", "checkout", "branch", "rm-branch", "reset", "merge"));

        if (!validCmd.contains(cmd)) {
            System.out.println("No command with that name exists.");
            System.exit(0);
        }

        if (args.length != n) {
            if (cmd.equals("commit") && args.length == 1) {
                System.out.println("Please enter a commit message.");
            } else {
                System.out.println("Incorrect operands.");
            }
            System.exit(0);
        }

        if (!cmd.equals("init") && !Repository.inGitWorkingDirectory()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

}
