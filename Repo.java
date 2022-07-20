import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Repo {
    private String HEAD;
    private HashMap<String, String> stagedFiles; // file name, sha1
    private ArrayList<String> removedFiles; // file name

    public Repo() throws IOException {
        stagedFiles = new HashMap<String, String>();
        removedFiles = new ArrayList<String>();
        Path pathTohead = Paths.get(".legit/branches/HEAD.txt");
        if (Files.exists(pathTohead)) {
            Stream<String> lines = Files.lines(pathTohead);
            List<String> collect = lines.collect(Collectors.toList());
            lines.close();
            HEAD = collect.get(0);
        }
        Path pathToStage = Paths.get(".legit/staging-area.txt");
        Path removed = Paths.get(".legit/removed-files.txt");
        if (Files.exists(pathToStage) && Files.exists(removed)) {
            byte[] stage = Files.readAllBytes(pathToStage);
            byte[] r = Files.readAllBytes(removed);
            stagedFiles = (HashMap) Utils.deserializeObject(stage);
            removedFiles = (ArrayList) Utils.deserializeObject(r);
        }
    }
    
    public void init() throws IOException{
        Path path = Paths.get(".legit");
        try {
            Files.createDirectory(path);
        } catch (FileAlreadyExistsException e) {
            System.out.print("A legit version-control system already exists in the current directory.");
        }
        Files.createDirectory(Paths.get(".legit/commits"));
        Files.createDirectory(Paths.get(".legit/branches"));
        Files.createDirectory(Paths.get(".legit/global-log"));
        Files.createDirectory(Paths.get(".legit/contents"));
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
        Files.write(Paths.get(".legit/removed-files.txt"), Utils.serializeObject(removedFiles));

        // Initializes first commit in /commits directory as a txt file, with sha1 as file name. 
        // Raw bytes of the commit object are written into the file.
        Commit initialCommit = new Commit("initial commit", new HashMap<String, String>(), "");
        Path initPath = Paths.get(".legit/commits/" + initialCommit.getHash() + ".txt");                
        Files.write(initPath, initialCommit.getBytes());

        // Creates a master.txt file in /branches, writes in initial commit sha1 as content.
        Path masterPath = Paths.get(".legit/branches/master.txt");
        Files.writeString(masterPath, initialCommit.getHash() + System.lineSeparator());

        // Creates a HEAD.txt file in /branches, writes the name of branch and the sha1 of the commit on separate lines.
        HEAD = "master";
        Path pathTohead = Paths.get(".legit/branches/HEAD.txt");
        Files.writeString(pathTohead, HEAD + System.lineSeparator());
        Files.writeString(pathTohead, initialCommit.getHash(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

    }

    public void add(String... fileNames) throws IOException {
        Path pathToStage = Paths.get(".legit/staging-area.txt");
        try {
            byte[] bytes = Files.readAllBytes(pathToStage);
            stagedFiles = (HashMap) Utils.deserializeObject(bytes);
        } catch(Exception e) {
            System.out.println("No staging area found.");
        }
        for(String name : fileNames) {
            try {
                byte[] data = Files.readAllBytes(Paths.get(name));
                String hash = Utils.sha1Hash(data);
                if(getCurrentCommit().getContents().containsValue(hash)) {
                    stagedFiles.remove(name, hash);
                    continue;
                }
                stagedFiles.put(name, hash);
                Path path = Paths.get(".legit/contents/" + hash + ".txt");
                Files.write(path, data);
            } catch(FileNotFoundException e) {
                System.out.print(name + " does not exist.");
            }
        }
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
    }

    public Commit getCurrentCommit() throws IOException {
        Path pathTohead = Paths.get(".legit/branches/HEAD.txt");
        try {
            Stream<String> lines = Files.lines(pathTohead);
            List<String> collect = lines.collect(Collectors.toList());
            lines.close();
            String hash = collect.get(1);
            String fileName = ".legit/commits/" + hash + ".txt";
            byte[] data = Files.readAllBytes(Paths.get(fileName));
            return (Commit) Utils.deserializeObject(data);
        } catch (Exception e) {
            System.out.println("Empty file.");
        }
        return null;
    }

    public void committing(String msg) throws IOException {
        Path pathToStage = Paths.get(".legit/staging-area.txt");
        Path removed = Paths.get(".legit/removed-files.txt");
        try {
            byte[] stage = Files.readAllBytes(pathToStage);
            byte[] r = Files.readAllBytes(removed);
            stagedFiles = (HashMap) Utils.deserializeObject(stage);
            removedFiles = (ArrayList) Utils.deserializeObject(r);
        } catch (Exception e) {
            System.out.println("Staging area or removed files not found.");
        }
        if (stagedFiles.isEmpty() && removedFiles.isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        } else if (msg.equals("")) {
            System.out.println("Please enter a commit message.");
            return;
        }
        Commit curr = getCurrentCommit();
        String currHash = curr.getHash();
        Map<String, String> newContents = (HashMap) curr.getContents().clone();
        List<String> stagedFileNames = new ArrayList<>(stagedFiles.keySet());
        for(String fileName : stagedFileNames) {
            newContents.put(fileName, stagedFiles.get(fileName));
        } for(String name : removedFiles) {
            newContents.remove(name);
        }
        Commit newCommit = new Commit(msg, (HashMap<String, String>) newContents, currHash);
        //new commit in /commits
        Files.write(Paths.get(".legit/commits/" + newCommit.getHash() + ".txt"), newCommit.getBytes());
        //update HEAD.txt
        Files.writeString(Paths.get(".legit/branches/HEAD.txt"), HEAD + System.lineSeparator());
        Files.writeString(Paths.get(".legit/branches/HEAD.txt"), newCommit.getHash(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        //update branches
        Files.writeString(Paths.get(".legit/branches/" + HEAD + ".txt"), newCommit.getHash() + System.lineSeparator());
        stagedFiles.clear();
        removedFiles.clear();
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
        Files.write(Paths.get(".legit/removed-files.txt"), Utils.serializeObject(removedFiles));
    }

    public void rm(String... fileNames) throws IOException {
        Path pathToStage = Paths.get(".legit/staging-area.txt");
        Path removed = Paths.get(".legit/removed-files.txt");
        try {
            byte[] stage = Files.readAllBytes(pathToStage);
            byte[] r = Files.readAllBytes(removed);
            stagedFiles = (HashMap) Utils.deserializeObject(stage);
            removedFiles = (ArrayList) Utils.deserializeObject(r);
        } catch (Exception e) {
            System.out.println("Staging area or removed files not found.");
        }
        if (!Files.exists(Paths.get(".legit"))) {
            throw new IllegalArgumentException("not .legit working directory");
        }
        for(String name : fileNames) {
            Commit curr = getCurrentCommit();
            if(!stagedFiles.containsKey(name) && !curr.getContents().containsKey(name)) {
                System.out.print("No reason to remove " + name);
                break;
            }
            if(curr.getContents().containsKey(name) && Files.isRegularFile(Paths.get(name))) {
                Files.delete(Paths.get(name));
                removedFiles.add(name);
            }
            stagedFiles.remove(name);
        }
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
        Files.write(Paths.get(".legit/removed-files.txt"), Utils.serializeObject(removedFiles));
    }

    public void log() throws IOException {
        Commit curr = getCurrentCommit();
        while (curr != null) {
            System.out.println("===");
            System.out.println("Commit " + curr.getHash());
            System.out.println(curr.getDatetime());
            System.out.println(curr.getMessage());
            System.out.println();
            if (curr.getParentHashes() != null) {
                String parent = ".legit/commits/" + curr.getParentHashes().get(0) + ".txt";
                byte[] data = Files.readAllBytes(Paths.get(parent));
                curr = (Commit) Utils.deserializeObject(data);
            } else {
                break;
            }
        }
    }

    public void globalLog() throws IOException {
        Files.walk(Paths.get(".legit/commits"))
                .filter(p -> p.toString().endsWith(".txt"))
                .forEach(p -> {
                    try {
                        byte[] data = Files.readAllBytes(p);
                        Commit c = (Commit) Utils.deserializeObject(data);
                        System.out.println("===");
                        System.out.println("Commit " + c.getHash());
                        System.out.println(c.getDatetime());
                        System.out.println(c.getMessage());
                        System.out.println();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    public void find(String msg) throws IOException {
        Files.walk(Paths.get(".legit/commits"))
                .filter(p -> p.toString().endsWith(".txt"))
                .forEach(p -> {
                    try {
                        byte[] data = Files.readAllBytes(p);
                        Commit c = (Commit) Utils.deserializeObject(data);
                        if(c.getMessage().equals(msg)) {
                            System.out.println("===");
                            System.out.println("Commit " + c.getHash());
                            System.out.println(c.getDatetime());
                            System.out.println(c.getMessage());
                            System.out.println();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    public void status() throws IOException {
        List<String> branches = new ArrayList<String>();
        try (Stream<Path> walk = Files.walk(Paths.get(".legit/branches"))) {
            branches = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        Path pathTohead = Paths.get(".legit/branches/HEAD.txt");
        try {
            Stream<String> lines = Files.lines(pathTohead);
            List<String> collect = lines.collect(Collectors.toList());
            lines.close();
            HEAD = collect.get(0);
        } catch (Exception e) {
            System.out.println("HEAD.txt not found in branches.");
        }
        branches.remove("HEAD");
        branches.remove(HEAD);
        branches.add("*" + HEAD);
        Collections.sort(branches);

        Path pathToStage = Paths.get(".legit/staging-area.txt");
        Path rem = Paths.get(".legit/removed-files.txt");
        try {
            byte[] stage = Files.readAllBytes(pathToStage);
            byte[] r = Files.readAllBytes(rem);
            stagedFiles = (HashMap) Utils.deserializeObject(stage);
            removedFiles = (ArrayList) Utils.deserializeObject(r);
        } catch (Exception e) {
            System.out.println("Staging area or removed files not found.");
        }
        List<String> staged = new ArrayList<>(stagedFiles.keySet());
        Collections.sort(staged);
        Collections.sort(removedFiles);

        List<String> currFiles = new ArrayList<String>();
        try (Stream<Path> walk = Files.walk(Paths.get(System.getProperty("user.dir")))) {
            currFiles = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> committedFiles = new ArrayList<>(getCurrentCommit().getContents().keySet());
        Collections.sort(currFiles);

        System.out.println("=== Branches ===");
        for (String branch : branches) {
            System.out.println(branch);
        }
        System.out.println();
        System.out.println("=== Staged Files ===");
        for (String stage : staged) {
            System.out.println(stage);
        }
        System.out.println();
        System.out.println("=== Removed Files ===");
        for (String removed : removedFiles) {
            System.out.println(removed);
        }
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();
        System.out.println("=== Untracked Files ===");
        for(String file : currFiles) {
            if(!committedFiles.contains(file)) {
                System.out.println(file);
            }
        }
        System.out.println();

    }

    public void checkout(String... args) throws IOException {
        Path pathToStage = Paths.get(".legit/staging-area.txt");
        Path removed = Paths.get(".legit/removed-files.txt");
        try {
            byte[] stage = Files.readAllBytes(pathToStage);
            byte[] r = Files.readAllBytes(removed);
            stagedFiles = (HashMap) Utils.deserializeObject(stage);
            removedFiles = (ArrayList) Utils.deserializeObject(r);
        } catch (Exception e) {
            System.out.println("Staging area or removed files not found.");
        }
        if(args.length == 2) {
            try {
                Commit c1 = getCurrentCommit();
                String contentsHash1 = c1.getContents().get(args[1]);
                Path fromFile1 = Paths.get(".legit/contents/" + contentsHash1 + ".txt");
                Path toFile1 = Paths.get(args[1]);
                Files.copy(fromFile1, toFile1, StandardCopyOption.REPLACE_EXISTING);
                stagedFiles.remove(args[1], contentsHash1);
            } catch (Exception e){
                System.out.println("File does not exist in that commit.");
            }
        } else if(args.length == 3) {
            Path commitPath2 = Paths.get(".legit/commits/" + args[0] + ".txt");
            try {
                byte[] data = Files.readAllBytes(commitPath2);
            } catch (Exception e) {
                System.out.println("No commit with that id exists.");
            }
            try {
                byte[] data = Files.readAllBytes(commitPath2);
                Commit c2 = (Commit) Utils.deserializeObject(data);
                String contentsHash2 = c2.getContents().get(args[2]);
                Path fromFile2 = Paths.get(".legit/contents/" + contentsHash2 + ".txt");
                Path toFile2 = Paths.get(args[2]);
                Files.copy(fromFile2, toFile2, StandardCopyOption.REPLACE_EXISTING);
                stagedFiles.remove(args[2], contentsHash2);
            } catch (Exception e) {
                System.out.println("File does not exist in that commit.");
            }
        } else if(args.length == 1) {
            Path branchPath = Paths.get(".legit/branches/" + args[0] + ".txt");
            if(!Files.exists(branchPath)){
                throw new FileNotFoundException("No such branch exists.");
            } if(HEAD.equals(args[0])) {
                throw new IOException("No need to checkout the current branch.");
            }
            Stream<String> lines = Files.lines(branchPath);
            List<String> collect = lines.collect(Collectors.toList());
            lines.close();
            String branchHead = collect.get(collect.size()-1);
            Path commitPath3 = Paths.get(".legit/commits/" + branchHead + ".txt");
            byte[] bytes = Files.readAllBytes(commitPath3);
            Commit c3 = (Commit) Utils.deserializeObject(bytes);
            List<String> fileNames = new ArrayList<String>(c3.getContents().keySet());
            List<String> committedFiles = new ArrayList<>(getCurrentCommit().getContents().keySet());
            for(String name : fileNames) {
                if(!committedFiles.contains(name)) {
                    throw new IOException("There is an untracked file in the way; delete it, or add and commit it first.");
                }
            }
            for(String name : fileNames) {
                String hash = c3.getContents().get(name);
                Path fromFile = Paths.get(".legit/contents/" + hash + ".txt");
                Path toFile = Paths.get(name);
                Files.copy(fromFile, toFile, StandardCopyOption.REPLACE_EXISTING);
                stagedFiles.remove(name, hash);
                removedFiles.add(name);
            }
            Path pathTohead = Paths.get(".legit/branches/HEAD.txt");
            HEAD = args[0];
            Files.writeString(pathTohead, HEAD + System.lineSeparator());
            Files.writeString(pathTohead, c3.getHash(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
        Files.write(Paths.get(".legit/removed-files.txt"), Utils.serializeObject(removedFiles));
    }

    public void branch(String branchName) throws IOException {
        Path branchPath = Paths.get(".legit/branches/" + branchName + ".txt");
        if (Files.exists(branchPath)) {
            throw new IOException("A branch with that name already exists.");
        } String hash = getCurrentCommit().getHash();
        Files.writeString(branchPath, hash + System.lineSeparator());
    }

    public void rmBranch(String branchName) throws IOException {
        Path branchPath = Paths.get(".legit/branches/" + branchName + ".txt");
        if(HEAD.equals(branchName)) {
            throw new IOException("Cannot remove the current branch.");
        } if(!Files.deleteIfExists(branchPath)) {
            throw new IOException("A branch with that name does not exist.");
        }
    }

    public void reset (String id) throws IOException {
        Path cPath = Paths.get(".legit/commits/" + id + ".txt");
        if(Files.exists(cPath)) {
            throw new IOException("No commit with that id exists.");
        }
        byte[] data = Files.readAllBytes(cPath);
        Commit c = (Commit) Utils.deserializeObject(data);
        List<String> resetFiles = new ArrayList<String>(c.getContents().keySet());
        List<String> currCommitFiles = new ArrayList<>(getCurrentCommit().getContents().keySet());
        for(String name : resetFiles) {
            if(!currCommitFiles.contains(name)) {
                throw new IOException("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }
        for(String name : resetFiles) {
            String hash = c.getContents().get(name);
            Path fromFile = Paths.get(".legit/contents/" + hash + ".txt");
            Path toFile = Paths.get(name);
            Files.copy(fromFile, toFile, StandardCopyOption.REPLACE_EXISTING);
            stagedFiles.remove(name, hash);
            removedFiles.add(name);
        }
        List<Path> txtFiles = Files.walk(Paths.get(".legit/branches"))
                .filter(p -> p.toString().endsWith(".txt"))
                .collect(Collectors.toList());
        for(Path p : txtFiles) {
            Stream<String> lines = Files.lines(p);
            List<String> collect = lines.collect(Collectors.toList());
            lines.close();
            if(collect.contains(id)) {
                HEAD = p.getFileName().toString();
                return;
            }
        }
        Path pathTohead = Paths.get(".legit/branches/HEAD.txt");
        Files.writeString(pathTohead, HEAD + System.lineSeparator());
        Files.writeString(pathTohead, c.getHash(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
        Files.write(Paths.get(".legit/removed-files.txt"), Utils.serializeObject(removedFiles));
    }
    
}
