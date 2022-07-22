import java.io.IOException;

public class Main {
    public static void main(String... args) throws IOException {
        Repo r = new Repo();
        int inputLength = args.length;
        if (inputLength == 0) {
            throw new IllegalArgumentException("Please enter a command.");
        } else {
            switch (args[0]) {
                case "init": {
                    if (checkInput(1, args)) {
                        r.init();
                    }
                    break;
                }
                case "add": {
                    for(int i=1; i<inputLength; i++) {
                        r.add(args[i]);
                    }
                    break;
                }
                case "commit": {
                    if (checkInput(2, args)) {
                        r.committing(args[1]);
                    }
                    break;
                }
                case "rm": {
                    for(int i=1; i<inputLength; i++) {
                        r.rm(args[i]);
                    }
                    break;
                }
                case "log":
                    if (checkInput(1, args)) {
                        r.log();
                    }
                    break;
                case "global-log":
                    if (checkInput(1, args)) {
                        r.globalLog();
                    }
                    break;
                case "find": {
                    if (checkInput(2, args)) {
                        r.find(args[1]);
                    }
                    break;
                }
                case "status": {
                    if (checkInput(1, args)) {
                        r.status();
                    }
                    break;
                }
                case "checkout": {
                    if (args.length != 2 && args.length != 3 && args.length != 4) {
                        throw new IllegalArgumentException("Incorrect Operands");
                    } else if ((args.length == 4 && !args[2].equals("--")) || (args.length == 3 && !args[1].equals("--"))) {
                        throw new IllegalArgumentException("Incorrect Operands");
                    } else {
                        r.checkout(args);
                    }
                    break;
                }
                case "branch": {
                    if (checkInput(2, args)) {
                        r.branch(args[1]);
                    }
                    break;
                }
                case "rm-branch": {
                    if (checkInput(2, args)) {
                        r.rmBranch(args[1]);
                    }
                    break;
                }
                case "reset": {
                    if (checkInput(2, args)) {
                        r.reset(args[1]);
                    }
                    break;
                }
                case "merge": {
                    if (checkInput(2, args)) {
                        r.merge(args[1]);
                    }
                    break;
                }
                default:
                    System.out.println("No command with that name exists.");
            }
        }
        System.exit(0);
    }


    static boolean checkInput(int length, String... args) throws IOException {
        if (args.length == length) {
            return true;
        }
        else throw new IllegalArgumentException("Incorrect Operands.");
    }
}
