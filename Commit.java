
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class Commit implements Serializable{
    private String message;
    private String hash;
    private ArrayList<String> parentHashes;
    private String datetime;
    private HashMap<String, String> contents; // file name, sha1

    public Commit(String msg, HashMap<String, String> content, String parent) {
        setDatetime();
        setHash();
        message = msg;
        contents = content;
        parentHashes.add(parent);
    }

    public byte[] getBytes() {
        return Utils.serializeObject(this);
    }

    public String getHash() {
        return hash;
    }

    public void setHash() {
        byte[] data = Utils.serializeObject(this);
        hash = Utils.sha1Hash(data);
    }

    public String getMessage() {
        return message;
    }

    public ArrayList<String> getParentHashes() {
        return parentHashes;
    }

    private void addParentHashes(String parent) {
        parentHashes.add(parent);
    }

    public String getDatetime() {
        return datetime;
    }

    private void setDatetime() {
        LocalDateTime current = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        datetime = current.format(formatter);
    }

    public HashMap<String, String> getContents() {
        return contents;
    }

}
