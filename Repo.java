import java.io.*;
import java.nio.file.*;
import java.sql.Array;
import java.util.*;
import java.util.stream.*;
import java.nio.charset.StandardCharsets;


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
        Commit initialCommit = new Commit("initial commit", new HashMap<String, String>(), null);
        Path initPath = Paths.get(".legit/commits/" + initialCommit.getHash() + ".txt");                
        Files.write(initPath, initialCommit.getBytes());

        // Creates a master.txt file in /branches, writes in initial commit sha1 as content.
        Path masterPath = Paths.get(".legit/branches/master.txt");
        Files.writeString(masterPath, initialCommit.getHash() + System.lineSeparator());

        // Creates a HEAD.txt file in /branches, writes the name of branch and the sha1 of the commit on separate lines.
        HEAD = "master";
        Path pathTohead = Paths.get(".legit/branches/HEAD.txt");
        Files.writeString(pathTohead, HEAD);
    }

    public void add(String... fileNames) throws IOException {
        for(String name : fileNames) {
            try {
                byte[] data = Files.readAllBytes(Paths.get(name));
                String hash = Utils.sha1Hash(data);
                if(getBranchHead(HEAD).getContents().containsValue(hash)) {
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

    public void committing(String msg) throws IOException {
        if (stagedFiles.isEmpty() && removedFiles.isEmpty()) {
            throw new IOException("No changes added to the commit.");
        } else if (msg.equals("")) {
            throw new IOException("Please enter a commit message.");
        }
        Commit curr = getBranchHead(HEAD);
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
        Files.writeString(Paths.get(".legit/branches/HEAD.txt"), HEAD);
        //update branches
        Files.writeString(Paths.get(".legit/branches/" + HEAD + ".txt"), newCommit.getHash() + System.lineSeparator());
        stagedFiles.clear();
        removedFiles.clear();
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
        Files.write(Paths.get(".legit/removed-files.txt"), Utils.serializeObject(removedFiles));
    }

    public void rm(String... fileNames) throws IOException {
        if (!Files.exists(Paths.get(".legit"))) {
            throw new IllegalArgumentException("not .legit working directory");
        }
        for(String name : fileNames) {
            Commit curr = getBranchHead(HEAD);
            if((!stagedFiles.containsKey(name) && !curr.getContents().containsKey(name)) || !Files.exists(Paths.get(name))) {
                throw new IOException("No reason to remove " + name);
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
        Commit curr = getBranchHead(HEAD);
        while (curr.getParentHash() != null) {
            System.out.println("===");
            System.out.println("Commit " + curr.getHash());
            System.out.println(curr.getDatetime());
            System.out.println(curr.getMessage());
            System.out.println();
            String parent = ".legit/commits/" + curr.getParentHash() + ".txt";
            byte[] data = Files.readAllBytes(Paths.get(parent));
            curr = (Commit) Utils.deserializeObject(data);
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
        branches.remove("HEAD");
        branches.remove(HEAD);
        branches.add("*" + HEAD);
        Collections.sort(branches);

        List<String> staged = new ArrayList<>(stagedFiles.keySet());
        Collections.sort(staged);
        Collections.sort(removedFiles);

        List<String> currFiles = new ArrayList<String>();
        try (Stream<Path> walk = Files.walk(Paths.get(System.getProperty("user.dir")))) {
            currFiles = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> committedFiles = new ArrayList<>(getBranchHead(HEAD).getContents().keySet());
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
        System.out.println("=== Untracked Files ===");
        for(String file : currFiles) {
            if(!committedFiles.contains(file)) {
                System.out.println(file);
            }
        }
        System.out.println();
    }

    public void checkout(String... args) throws IOException {
        if(args.length == 2) {
            try {
                Commit c1 = getBranchHead(HEAD);
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
            Commit c3 = getBranchHead(args[0]);
            List<String> fileNames = new ArrayList<String>(c3.getContents().keySet());
            List<String> committedFiles = new ArrayList<>(getBranchHead(HEAD).getContents().keySet());
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
            Files.writeString(pathTohead, HEAD);
        }
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
        Files.write(Paths.get(".legit/removed-files.txt"), Utils.serializeObject(removedFiles));
    }

    public void branch(String branchName) throws IOException {
        Path branchPath = Paths.get(".legit/branches/" + branchName + ".txt");
        if (Files.exists(branchPath)) {
            throw new IOException("A branch with that name already exists.");
        } String hash = getBranchHead(HEAD).getHash();
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
        List<String> currCommitFiles = new ArrayList<>(getBranchHead(HEAD).getContents().keySet());
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
        Files.writeString(pathTohead, HEAD);
        Files.write(Paths.get(".legit/staging-area.txt"), Utils.serializeObject(stagedFiles));
        Files.write(Paths.get(".legit/removed-files.txt"), Utils.serializeObject(removedFiles));
    }

    //returns the head of a branch if isHead is true, and returns the end of the branch otherwise. getBranchHead(HEAD) returns current commit
    public Commit getBranchHead(String branch) throws IOException {
        Stream<String> lines = Files.lines(Paths.get(".legit/branches/" + branch + ".txt"));
        List<String> collect = lines.collect(Collectors.toList());
        lines.close();
        String fileName = ".legit/commits/" + collect.get(collect.size()-1) + ".txt";
        byte[] data = Files.readAllBytes(Paths.get(fileName));
        return (Commit) Utils.deserializeObject(data);
    }

    public boolean mergeErrors(String branch) throws IOException {
        if (!stagedFiles.isEmpty() || !removedFiles.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return true;
        } else if (!Files.exists(Paths.get(".legit/branches/" + branch + ".txt"))) {
            System.out.println("A branch with that name does not exist.");
            return true;
        } else if (branch.equals(HEAD)) {
            System.out.println("Cannot merge a branch with itself.");
            return true;
        }
        Commit c = getBranchHead(branch);
        List<String> fileNames = new ArrayList<String>(c.getContents().keySet());
        List<String> committedFiles = new ArrayList<>(getBranchHead(HEAD).getContents().keySet());
        for(String name : fileNames) {
            if(!committedFiles.contains(name)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return true;
            }
        }
        return false;
    }

    //the most recent commit that is a parent to a node on both branches should be the most recent common ancestor
    public Commit findSplitPoint(String branch) throws IOException {
        Commit curr = getBranchHead(HEAD);
        Commit cPtr = curr;
        Map<String, Commit> map = new HashMap<String, Commit>();

        //get given branch's commit hashes, put parenthash, commit into hashmap
        Stream<String> lines1 = Files.lines(Paths.get(".legit/branches/" + branch + ".txt"));
        List<String> branchHashes = lines1.collect(Collectors.toList());
        lines1.close();
        for(String hash : branchHashes) {
            String fileName = ".legit/commits/" + hash + ".txt";
            byte[] data = Files.readAllBytes(Paths.get(fileName));
            Commit c = (Commit) Utils.deserializeObject(data);
            map.put(c.getParentHash(), c);
        }
        //in current branch work back from current commit and check if it has a parent reference that is contained in the hashmap
        boolean found = false;
        while(cPtr.getParentHash() != null) {
            if(map.containsKey(cPtr.getParentHash())) found = true;
            String parent = ".legit/commits/" + cPtr.getParentHash() + ".txt";
            byte[] data = Files.readAllBytes(Paths.get(parent));
            cPtr = (Commit) Utils.deserializeObject(data);
            if(found) break;
        }
        if(!found) {
            throw new IOException("There was an error finding the split point.");
        }
        return cPtr;
    }

    public void mergeLineByLine(Path currP, Path branchP) throws IOException {
        try (BufferedReader reader1 = Files.newBufferedReader(currP);
             BufferedReader reader2 = Files.newBufferedReader(branchP)) {
            List<String> lines = new ArrayList<String>();
            Queue<String> lines2 = new ArrayDeque<String>();
            int lineNum = 1, prev = 1;
            String line1 = reader1.readLine();
            String line2 = reader2.readLine();
            while (line1 != null || line2 != null) {
                if(line1 == null || line2 == null) {
                    if(lineNum - prev >1) {
                        lines.add("<<<<<<< HEAD");
                    }
                    lines.add(line1);
                    lines2.add(line2);
                    prev = lineNum;
                } else if (! line1.equalsIgnoreCase(line2)) {
                    if(lineNum - prev >1) {
                        lines.add("<<<<<<< HEAD");
                    }
                    lines.add(line1);
                    lines2.add(line2);
                    prev = lineNum;
                } else if(!lines2.isEmpty()) {
                    lines.add("=======");
                    for(String item : lines2) {
                        lines.add(item);
                    }
                    lines.add(">>>>>>>");
                    lines2.clear();
                    lines.add(line1);
                } else lines.add(line1);
                line1 = reader1.readLine();
                line2 = reader2.readLine();
                lineNum++;
            }
            for(String s : lines) {
                if(s==null) lines.remove(s);
            }
            reader1.close();
            reader2.close();
            Files.write(currP, lines, StandardCharsets.UTF_8);
        }
    }

    public void mergeEmpty(Path currP, Path branchP) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("<<<<<<< HEAD");
        if(!Files.exists(currP)) {
            try(BufferedReader reader = Files.newBufferedReader(branchP)) {
                lines.add("=======");
                String line1 = reader.readLine();
                while (line1 != null) {
                    lines.add(line1);
                    line1 = reader.readLine();
                }
                reader.close();
            }
        } else if(!Files.exists(branchP)) {
            try(BufferedReader reader = Files.newBufferedReader(currP)) {
                String line1 = reader.readLine();
                while (line1 != null) {
                    lines.add(line1);
                    line1 = reader.readLine();
                }
                reader.close();
                lines.add("=======");
            }
        }
        lines.add(">>>>>>>");
        Files.write(currP, lines, StandardCharsets.UTF_8);
    }

    public void merge(String branch) throws IOException {
        if(mergeErrors(branch)) return;
        Commit split = findSplitPoint(branch);
        if(split.equals(getBranchHead(branch))){
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }
        //get current branch's commit hashes
        Stream<String> lines2 = Files.lines(Paths.get(".legit/branches/" + HEAD + ".txt"));
        List<String> currBranchHashes = lines2.collect(Collectors.toList());
        lines2.close();
        if(currBranchHashes.contains(split.getHash())) {
            checkout(branch);
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        Commit curr = getBranchHead(HEAD);
        Commit branchHead = getBranchHead(branch);
        HashMap<String, String> splitContents = split.getContents();
        HashMap<String, String> currContents = curr.getContents();
        HashMap<String, String> branchHeadContents = branchHead.getContents();
        List<String> splitFiles = new ArrayList<>(splitContents.keySet());
        List<String> branchFiles = new ArrayList<>(branchHeadContents.keySet());
        List<String> currFiles = new ArrayList<>(currContents.keySet());
        for(String file : splitFiles) {
            if(currContents.containsKey(file) && !branchHeadContents.containsKey(file)) {
                if(currContents.get(file).equals(splitContents.get(file))) {
                    Files.delete(Paths.get(file));
                    removedFiles.add(file);
                    stagedFiles.remove(file);
                }
            } else if(currContents.containsKey(file) && branchHeadContents.containsKey(file)) {
                if(!splitContents.get(file).equals(branchHeadContents.get(file)) && splitContents.get(file).equals(currContents.get(file))) {
                    checkout(branchHead.getHash(), "--", file);
                    stagedFiles.put(file, branchHeadContents.get(file));
                }
            }
        } for(String file : branchFiles) {
            if(!splitContents.containsKey(file) && !currContents.containsKey(file)) {
                checkout(branchHead.getHash(), "--", file);
                stagedFiles.put(file, branchHeadContents.get(file));
            }
        } for (String file : branchFiles) {
            if (!splitContents.containsKey(file) ||
                    (!splitContents.get(file).equals(branchHeadContents.get(file)) && !splitContents.get(file).equals(currContents.get(file)))) {
                if (currFiles.contains(file) && !currContents.get(file).equals(branchHeadContents.get(file))) {
                    mergeLineByLine(Paths.get(".legit/contents/" + currContents.get(file) + ".txt"),
                            Paths.get(".legit/contents/" + branchHeadContents.get(file) + ".txt"));
                } else if (!currFiles.contains(file)) {
                    mergeEmpty(Paths.get(".legit/contents/" + currContents.get(file) + ".txt"),
                            Paths.get(".legit/contents/" + branchHeadContents.get(file) + ".txt"));
                }
            }
        } for(String file : currFiles) {
            if (!splitContents.containsKey(file) ||
                    (!splitContents.get(file).equals(branchHeadContents.get(file)) && !splitContents.get(file).equals(currContents.get(file)))) {
                if (branchFiles.contains(file) && !currContents.get(file).equals(branchHeadContents.get(file))) {
                    mergeLineByLine(Paths.get(".legit/contents/" + currContents.get(file) + ".txt"),
                            Paths.get(".legit/contents/" + branchHeadContents.get(file) + ".txt"));
                } else if (!branchFiles.contains(file)) {
                    mergeEmpty(Paths.get(".legit/contents/" + currContents.get(file) + ".txt"),
                            Paths.get(".legit/contents/" + branchHeadContents.get(file) + ".txt"));
                }
            }
        }
    }
}
